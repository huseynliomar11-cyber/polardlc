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

public class HolyWorldRotation extends RotationsSystem implements QClient {

    private final Aura aura;
    private LivingEntity trackedTarget;
    private float lastYaw;
    private float lastPitch;
    private boolean initialized;
    private int attackSlowdownTicks = 0;

    // 2nd-order physics controller tuned for HolyWorld snappy aim and crit focus
    private final SecondOrderPhysicsController controller = new SecondOrderPhysicsController(
            22.0f,  // Natural frequency omega_n (sub-stepped, stable)
            0.94f,  // Damping ratio zeta
            820.0f, // Max yaw velocity (deg/s)
            500.0f, // Max pitch velocity (deg/s)
            5500.0f,// Max acceleration (deg/s^2)
            32000.0f,// Max jerk (deg/s^3)
            0.12f,  // OU noise tau (s)
            70.0f   // OU noise sigma
    );

    public HolyWorldRotation(Aura aura) {
        this.aura = aura;
    }

    public void reset() {
        trackedTarget = null;
        attackSlowdownTicks = 0;
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
        attackSlowdownTicks = 3;
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
            controller.reset();
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
        }

        // Target upper body (chest/head) for max crit chance on HolyWorld
        Box box = target.getBoundingBox();
        Vec3d aimPoint = new Vec3d(
                box.minX + (box.maxX - box.minX) * 0.5D,
                box.minY + (box.maxY - box.minY) * 0.72D,
                box.minZ + (box.maxZ - box.minZ) * 0.5D
        );

        if (shouldUseElytraPredict(target)) {
            aimPoint = getPredictedPoint(target, aimPoint);
        }

        Vec2f targetRot = RotationUtils.getRotations(aimPoint);
        float targetYaw = targetRot.x;
        float targetPitch = targetRot.y;

        boolean readyToHit = mc.player.getAttackCooldownProgress(1.0F) > 0.88F;

        // Dynamic acceleration boost for HolyWorld hit timings
        controller.setNaturalFrequency(readyToHit ? 25.0f : 21.0f);
        controller.setMaxVelocityYaw(readyToHit ? 850.0f : 700.0f);

        if (attackSlowdownTicks > 0) {
            attackSlowdownTicks--;
            controller.setMaxVelocityYaw(520.0f);
        }

        // Step physics model (dt = 0.05s)
        Vec2f angularDelta = controller.step(lastYaw, lastPitch, targetYaw, targetPitch, 0.05f);

        float newYaw = lastYaw + angularDelta.x;
        float newPitch = MathHelper.clamp(lastPitch + angularDelta.y, -89.0F, 89.0F);

        // Single-point authoritative quantization handled by RotationStorage
        Rotation rot = new Rotation(newYaw, newPitch);
        RotationStorage.update(rot, 360.0F, 360.0F, 50.0F, 50.0F, 0, 1, Aura.clientLook.isState());

        // Sync with actual applied player state to eliminate drift
        lastYaw = mc.player.getYaw();
        lastPitch = mc.player.getPitch();
        rotate = new Vec2f(lastYaw, lastPitch);
    }
}
