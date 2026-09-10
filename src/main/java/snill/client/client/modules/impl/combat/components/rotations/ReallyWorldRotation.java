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

    // Pitch damping and drifting for Matrix bypass
    private double driftAngle;

    // 2nd-order physics controller with overdamped characteristics to bypass Matrix angle-derivative checks
    private final SecondOrderPhysicsController controller = new SecondOrderPhysicsController(
            20.0f,  // Natural frequency omega_n
            1.05f,  // Damping ratio zeta (slightly overdamped for buttery, non-spiking tracking)
            700.0f, // Max yaw velocity (deg/s)
            380.0f, // Max pitch velocity (deg/s - Matrix heavily checks pitch acceleration)
            4000.0f,// Max acceleration (deg/s^2)
            20000.0f,// Max jerk (deg/s^3)
            0.16f,  // OU noise tau
            60.0f   // OU noise sigma
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
            rotate = new Vec2f(mc.player.getYaw(), mc.player.getPitch());
            lastYaw = rotate.x;
            lastPitch = rotate.y;
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
        }

        driftAngle += 0.06D;

        // Subtle elliptical drift across the body to avoid Matrix static angle detection
        Box box = target.getBoundingBox();
        double width = (box.maxX - box.minX) * 0.32D;
        double height = (box.maxY - box.minY) * 0.18D;

        Vec3d center = box.getCenter();
        Vec3d aimPoint = new Vec3d(
                center.x + Math.sin(driftAngle) * width,
                box.minY + (box.maxY - box.minY) * 0.65D + Math.cos(driftAngle * 0.8D) * height,
                center.z + Math.cos(driftAngle) * width
        );

        if (shouldUseElytraPredict(target)) {
            aimPoint = getPredictedPoint(target, aimPoint);
        }

        Vec2f targetRot = RotationUtils.getRotations(aimPoint);
        float targetYaw = targetRot.x;
        float targetPitch = targetRot.y;

        // Step physics model
        Vec2f angularDelta = controller.step(lastYaw, lastPitch, targetYaw, targetPitch, 0.05f);

        float newYaw = lastYaw + angularDelta.x;
        float newPitch = MathHelper.clamp(lastPitch + angularDelta.y, -89.0F, 89.0F);

        // Authoritative single quantization via RotationStorage
        Rotation rot = new Rotation(newYaw, newPitch);
        RotationStorage.update(rot, 360.0F, 360.0F, 45.0F, 35.0F, 0, 1, Aura.clientLook.isState());

        rotate = new Vec2f(rot.getYaw(), rot.getPitch());
        lastYaw = rot.getYaw();
        lastPitch = rot.getPitch();
    }
}
