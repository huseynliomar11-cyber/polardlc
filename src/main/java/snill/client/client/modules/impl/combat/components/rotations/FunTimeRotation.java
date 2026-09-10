package snill.client.client.modules.impl.combat.components.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import snill.client.api.QClient;
import snill.client.api.storages.implement.RotationStorage;
import snill.client.api.utils.rotate.Rotation;
import snill.client.api.utils.rotate.RotationUtils;
import snill.client.client.modules.impl.combat.Aura;
import snill.client.client.modules.impl.combat.components.RotationsSystem;
import snill.client.client.modules.impl.combat.components.interpolation.BestPoint;
import snill.client.client.modules.impl.combat.components.rotations.physics.SecondOrderPhysicsController;

public class FunTimeRotation extends RotationsSystem implements QClient {

    private final Aura aura;
    private LivingEntity trackedTarget;
    private float lastYaw;
    private float lastPitch;
    private boolean initialized;
    private int attackSlowdownTicks = 0;

    // 2nd-order mass-spring-damper controller tuned for FunTime / GrimAC
    private final SecondOrderPhysicsController controller = new SecondOrderPhysicsController(
            20.0f,  // Natural frequency omega_n (sub-stepped, unconditionally stable)
            0.98f,  // Damping ratio zeta (critically damped)
            720.0f, // Max yaw velocity (deg/s)
            480.0f, // Max pitch velocity (deg/s)
            5000.0f,// Max acceleration (deg/s^2)
            30000.0f,// Max jerk (deg/s^3)
            0.14f,  // OU noise correlation tau (s)
            85.0f   // OU noise sigma on acceleration
    );

    public FunTimeRotation(Aura aura) {
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
        // Slow down camera acceleration/velocity for 3 ticks after hitting
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

        // Find best multipoint on hitbox (chest / head area)
        Vec3d point = BestPoint.getMultipoint(target, 128.0);
        if (point == null) {
            point = target.getBoundingBox().getCenter();
        }
        if (shouldUseElytraPredict(target)) {
            point = getPredictedPoint(target, point);
        }

        Vec2f angle = RotationUtils.getRotations(point);
        float targetYaw = angle.x;
        float targetPitch = angle.y;

        double targetDistance = mc.player.getEyePos().distanceTo(point);
        Vec3d targetOffset = point.subtract(mc.player.getPos());
        Vec3d flightVelocity = new Vec3d(mc.player.getVelocity().x, 0.0D, mc.player.getVelocity().z);
        boolean passedTarget = mc.player.isGliding()
                && flightVelocity.lengthSquared() > 0.0025D
                && flightVelocity.normalize().dotProduct(new Vec3d(targetOffset.x, 0.0D, targetOffset.z)) < -0.45D;

        if (mc.player.isGliding() && targetDistance <= 4.25D) {
            targetPitch = MathHelper.clamp(targetPitch, -35.0F, 25.0F);
        }

        boolean readyToAttack = mc.player.getAttackCooldownProgress(1.0F) > 0.85F && aura.getWhiteRiseTicksToAttack() <= 1;

        // Adapt controller dynamics dynamically
        if (mc.player.isGliding()) {
            controller.setNaturalFrequency(passedTarget ? 16.0f : 22.0f);
            controller.setMaxVelocityYaw(passedTarget ? 1000.0f : 800.0f);
            controller.setMaxVelocityPitch(550.0f);
            controller.setDampingRatio(1.02f);
        } else {
            controller.setNaturalFrequency(readyToAttack ? 23.0f : 19.0f);
            controller.setMaxVelocityYaw(readyToAttack ? 740.0f : 620.0f);
            controller.setMaxVelocityPitch(readyToAttack ? 440.0f : 360.0f);
            controller.setDampingRatio(readyToAttack ? 0.96f : 1.0f);
        }

        // Apply attack deceleration window if recently swung
        if (attackSlowdownTicks > 0) {
            attackSlowdownTicks--;
            controller.setMaxVelocityYaw(mc.player.isGliding() ? 550.0f : 480.0f);
        }

        // Physical step with guaranteed numerical stability via sub-stepping
        Vec2f angularDelta = controller.step(lastYaw, lastPitch, targetYaw, targetPitch, 0.05f);

        float newYaw = lastYaw + angularDelta.x;
        float newPitch = MathHelper.clamp(lastPitch + angularDelta.y, -89.0F, 89.0F);

        // RotationStorage performs the single-point authoritative quantization
        Rotation finalRot = new Rotation(newYaw, newPitch);
        RotationStorage.update(finalRot, 360.0F, 360.0F, 46.0F, 46.0F, 0, 1, Aura.clientLook.isState());

        // Reconcile physical velocity with actual quantized applied displacement
        controller.reconcileAppliedDelta(RotationStorage.lastAppliedYawDelta, RotationStorage.lastAppliedPitchDelta, 0.05f);

        // Sync lastYaw and lastPitch with the ACTUAL committed player state to prevent drift
        lastYaw = mc.player.getYaw();
        lastPitch = mc.player.getPitch();
        rotate = new Vec2f(lastYaw, lastPitch);
    }
}
