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

public class SuperLegitRotation extends RotationsSystem implements QClient {

    private final Aura aura;
    private LivingEntity trackedTarget;
    private float lastYaw;
    private float lastPitch;
    private boolean initialized;
    private int ticks;

    // Human initial target acquisition overshoot state
    private float overshootYaw = 0.0F;
    private float overshootPitch = 0.0F;
    private boolean isOvershooting = false;

    // 2nd-order underdamped controller (zeta = 0.84) creating realistic human settle with low-frequency neuromuscular noise
    private final SecondOrderPhysicsController controller = new SecondOrderPhysicsController(
            14.0f,  // Natural frequency omega_n (calm human tracking speed, unconditionally stable)
            0.84f,  // Damping ratio zeta (subtle natural human overshoot)
            450.0f, // Max yaw velocity (deg/s)
            320.0f, // Max pitch velocity (deg/s)
            2600.0f,// Max acceleration (deg/s^2)
            12000.0f,// Max jerk (deg/s^3)
            0.18f,  // OU noise tau
            120.0f  // OU noise sigma
    );

    public SuperLegitRotation(Aura aura) {
        this.aura = aura;
    }

    public void reset() {
        trackedTarget = null;
        ticks = 0;
        overshootYaw = 0.0F;
        overshootPitch = 0.0F;
        isOvershooting = false;
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
            ticks = 0;
            controller.reset();
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
            // Subtle initial human flick overshoot when locking onto a target
            overshootYaw = (float) ((Math.random() - 0.5D) * 2.5D);
            overshootPitch = (float) ((Math.random() - 0.5D) * 1.4D);
            isOvershooting = true;
        }

        ticks++;

        // Aim at chest with slight natural breathing offset
        Box box = target.getBoundingBox();
        double breathY = Math.sin(ticks * 0.08D) * 0.04D;
        Vec3d aimPoint = new Vec3d(
                box.minX + (box.maxX - box.minX) * 0.5D,
                box.minY + (box.maxY - box.minY) * 0.65D + breathY,
                box.minZ + (box.maxZ - box.minZ) * 0.5D
        );

        if (shouldUseElytraPredict(target)) {
            aimPoint = getPredictedPoint(target, aimPoint);
        }

        Vec2f targetRot = RotationUtils.getRotations(aimPoint);
        float targetYaw = targetRot.x;
        float targetPitch = targetRot.y;

        // Apply decay to acquisition overshoot
        if (isOvershooting) {
            targetYaw += overshootYaw;
            targetPitch += overshootPitch;
            overshootYaw *= 0.80F;
            overshootPitch *= 0.80F;
            if (Math.abs(overshootYaw) < 0.15F && Math.abs(overshootPitch) < 0.15F) {
                isOvershooting = false;
            }
        }

        // Step physics model (dt = 0.05s)
        Vec2f angularDelta = controller.step(lastYaw, lastPitch, targetYaw, targetPitch, 0.05f);

        float newYaw = lastYaw + angularDelta.x;
        float newPitch = MathHelper.clamp(lastPitch + angularDelta.y, -89.0F, 89.0F);

        // Single-point authoritative quantization via RotationStorage
        Rotation rot = new Rotation(newYaw, newPitch);
        RotationStorage.update(rot, 360.0F, 360.0F, 40.0F, 30.0F, 0, 1, Aura.clientLook.isState());

        // Reconcile physical velocity with actual quantized applied displacement
        controller.reconcileAppliedDelta(RotationStorage.lastAppliedYawDelta, RotationStorage.lastAppliedPitchDelta, 0.05f);

        // Sync with actual applied player state to eliminate drift
        lastYaw = mc.player.getYaw();
        lastPitch = mc.player.getPitch();
        rotate = new Vec2f(lastYaw, lastPitch);
    }
}
