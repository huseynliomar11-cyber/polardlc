package snill.client.client.modules.impl.combat.components.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import snill.client.api.QClient;
import snill.client.api.storages.implement.RotationStorage;
import snill.client.api.utils.rotate.Rotation;
import snill.client.api.utils.rotate.RotationUtils;
import snill.client.client.modules.impl.combat.Aura;
import snill.client.client.modules.impl.combat.components.RotationsSystem;
import snill.client.client.modules.impl.combat.components.rotations.physics.SecondOrderPhysicsController;

public class ReallyWorldRotation extends RotationsSystem implements QClient {

    private final Aura aura;
    private LivingEntity trackedTarget;
    private float lastYaw;
    private float lastPitch;
    private boolean initialized;

    // Smooth stochastic drift across target bounds to avoid Matrix static angle checks
    private double driftAngle;

    // 2nd-order physics controller with overdamped characteristics to bypass Matrix angle-derivative checks
    private final SecondOrderPhysicsController controller = new SecondOrderPhysicsController(
            18.0f,  // Natural frequency omega_n (stable with sub-stepping)
            1.05f,  // Damping ratio zeta (slightly overdamped for buttery, non-spiking tracking)
            680.0f, // Max yaw velocity (deg/s)
            360.0f, // Max pitch velocity (deg/s - Matrix heavily checks pitch acceleration)
            3800.0f,// Max acceleration (deg/s^2)
            18000.0f,// Max jerk (deg/s^3)
            0.16f,  // OU noise tau
            55.0f   // OU noise sigma
    );

    public ReallyWorldRotation(Aura aura) {
        this.aura = aura;
    }

    public void reset() {
        trackedTarget = null;
        driftAngle = 0.0D;
        controller.reset();
        initialized = mc.player != null;
        if (mc.player != null) {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
        } else {
            lastYaw = 0.0F;
            lastPitch = 0.0F;
        }
    }

    public void onAttack() {
    }

    @Override
    public void updateRotations(LivingEntity target) {
        if (mc.player == null || target == null) return;

        if (mc.player.isBlocking()) {
            controller.reset();
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
            rotate = new Vec2f(lastYaw, lastPitch);
            return;
        }

        if (!initialized) {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
            initialized = true;
        }

        if (trackedTarget != target) {
            trackedTarget = target;
            driftAngle = Math.random() * Math.PI * 2;
            controller.reset();
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
        }

        // Variational non-periodic drift across the body
        driftAngle += 0.04D + Math.random() * 0.03D;

        Box box = target.getBoundingBox();
        double width = (box.maxX - box.minX) * 0.28D;
        double height = (box.maxY - box.minY) * 0.16D;

        Vec3d center = box.getCenter();
        Vec3d aimPoint = new Vec3d(
                center.x + Math.sin(driftAngle) * width,
                box.minY + (box.maxY - box.minY) * 0.65D + Math.cos(driftAngle * 0.85D) * height,
                center.z + Math.cos(driftAngle) * width
        );

        if (shouldUseElytraPredict(target)) {
            aimPoint = getPredictedPoint(target, aimPoint);
        }

        Vec2f targetRot = RotationUtils.getRotations(aimPoint);
        float targetYaw = targetRot.x;
        float targetPitch = targetRot.y;

        // Step physics model with sub-stepping stability
        Vec2f angularDelta = controller.step(lastYaw, lastPitch, targetYaw, targetPitch, 0.05f);

        float newYaw = lastYaw + angularDelta.x;
        float newPitch = MathHelper.clamp(lastPitch + angularDelta.y, -89.0F, 89.0F);

        // Authoritative single quantization via RotationStorage
        Rotation rot = new Rotation(newYaw, newPitch);
        RotationStorage.update(rot, 360.0F, 360.0F, 45.0F, 35.0F, 0, 1, Aura.clientLook.isState());

        // Reconcile physical velocity with actual quantized applied displacement
        controller.reconcileAppliedDelta(RotationStorage.lastAppliedYawDelta, RotationStorage.lastAppliedPitchDelta, 0.05f);

        // Sync with actual applied player state to eliminate drift
        lastYaw = mc.player.getYaw();
        lastPitch = mc.player.getPitch();
        rotate = new Vec2f(lastYaw, lastPitch);
    }
}
