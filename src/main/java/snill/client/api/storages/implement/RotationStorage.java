package snill.client.api.storages.implement;

import snill.client.api.QClient;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.util.math.MathHelper;
import snill.client.api.events.EventInvoker;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventKeyboardInput;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.rotate.Rotation;
import snill.client.api.utils.rotate.RotationUtils;
import snill.client.client.modules.impl.combat.components.gcd.GCDUtil;
import ru.virtuoz.convert.Convert;

@Getter
@Setter
@Accessors(fluent = true)
@Convert(Convert.ConvertType.MUTATION)

public class RotationStorage implements QClient {

    public static RotationStorage instance;

    public RotationStorage() {
        instance = this;
        EventInvoker.register(this);
    }

    private RotationTask currentTask = RotationTask.IDLE;
    private float currentYawSpeed;
    private float currentPitchSpeed;
    private float currentYawReturnSpeed;
    private float currentPitchReturnSpeed;
    private int currentPriority;
    private int currentTimeout;
    private int idleTicks;
    private Rotation targetRotation;

    public static double direction(float rotationYaw, final float moveForward, final float moveStrafing) {
        if (moveForward < 0F) rotationYaw += 180F;
        float forward = 1F;
        if (moveForward < 0F) forward = -0.5F;
        if (moveForward > 0F) forward = 0.5F;
        if (moveStrafing > 0F) rotationYaw -= 90F * forward;
        if (moveStrafing < 0F) rotationYaw += 90F * forward;
        return Math.toRadians(rotationYaw);
    }

    public static void fixMovement(final EventKeyboardInput event, final float yaw) {
        final float forward = event.getMovementForward();
        final float strafe = event.getMovementSideways();

        if (forward == 0 && strafe == 0) {
            return;
        }

        final double targetAngle = MathHelper.wrapDegrees(Math.toDegrees(direction(yaw, forward, strafe)));

        float bestForward = 0, bestStrafe = 0;
        float smallestDifference = Float.MAX_VALUE;

        for (float testForward = -1F; testForward <= 1F; testForward++) {
            for (float testStrafe = -1F; testStrafe <= 1F; testStrafe++) {
                if (testForward == 0 && testStrafe == 0) continue;

                float playerYaw = mc.player != null ? mc.player.getYaw() : yaw;
                final double testAngle = MathHelper.wrapDegrees(Math.toDegrees(direction(playerYaw, testForward, testStrafe)));
                final float difference = Math.abs(MathHelper.wrapDegrees((float)(targetAngle - testAngle)));

                if (difference < smallestDifference) {
                    smallestDifference = difference;
                    bestForward = testForward;
                    bestStrafe = testStrafe;
                }
            }
        }

        event.setMovementForward(bestForward);
        event.setMovementSideways(bestStrafe);
    }


    @EventLink
    public void onInput(final EventKeyboardInput event) {
        if (isRotating()) {
            fixMovement(event, MathHelper.wrapDegrees(mc.gameRenderer.getCamera().getYaw()));
        }
    }

    private void resetRotation() {
        Rotation targetRotation = new Rotation(FreeLookStorage.getFreeYaw(), FreeLookStorage.getFreePitch());
        if (updateRotation(targetRotation, currentYawReturnSpeed(), currentPitchReturnSpeed())) {
            stopRotation();
        }
    }

    @EventLink
    public void onEventTick(EventUpdate event) {
        if (currentTask().equals(RotationTask.AIM) && idleTicks() > currentTimeout()) {
            currentTask(RotationTask.RESET);
        }

        if (currentTask().equals(RotationTask.RESET)) {
            resetRotation();
        }
        idleTicks++;
    }

    public static void update(Rotation target, float yawSpeed, float pitchSpeed, float yawReturnSpeed, float pitchReturnSpeed, int timeout, int priority, boolean clientRotation) {
        final RotationStorage instance = RotationStorage.instance;
        if (mc.player == null) return;
        if (instance.currentPriority() > priority) {
            return;
        }

        if (instance.currentTask().equals(RotationTask.IDLE) && !clientRotation) {
            FreeLookStorage.setActive(true);
        }

        instance.currentYawSpeed(yawSpeed);
        instance.currentPitchSpeed(pitchSpeed);
        instance.currentYawReturnSpeed(yawReturnSpeed);
        instance.currentPitchReturnSpeed(pitchReturnSpeed);
        instance.currentTimeout(timeout);
        instance.currentPriority(priority);
        instance.currentTask(RotationTask.AIM);
        instance.targetRotation(target);

        instance.updateRotation(target, yawSpeed, pitchSpeed);
    }

    public static void update(Rotation targetRotation, float turnSpeed, float returnSpeed, int timeout, int priority) {
        update(targetRotation, turnSpeed, turnSpeed, returnSpeed, returnSpeed, timeout, priority, false);
    }

    public static void update(Rotation targetRotation, float yawSpeed, float pitchSpeed, float returnSpeed, int timeout, int priority) {
        update(targetRotation, yawSpeed, pitchSpeed, returnSpeed, returnSpeed, timeout, priority, false);
    }

    private final snill.client.client.modules.impl.combat.components.rotations.physics.InputQuantizer quantizer = new snill.client.client.modules.impl.combat.components.rotations.physics.InputQuantizer();

    public static float lastAppliedYawDelta = 0.0f;
    public static float lastAppliedPitchDelta = 0.0f;

    public static void resetQuantizer() {
        if (instance != null) {
            instance.quantizer.reset();
        }
    }

    private boolean updateRotation(Rotation targetRotation, float yawSpeed, float pitchSpeed) {
        if (mc.player == null) return false;

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDelta = MathHelper.wrapDegrees(targetRotation.getYaw() - currentYaw);
        float pitchDelta = targetRotation.getPitch() - currentPitch;

        float clampedYaw = Math.min(Math.abs(yawDelta), yawSpeed);
        float clampedPitch = Math.min(Math.abs(pitchDelta), pitchSpeed);

        float rawDeltaYaw = MathHelper.clamp(yawDelta, -clampedYaw, clampedYaw);
        float rawDeltaPitch = MathHelper.clamp(pitchDelta, -clampedPitch, clampedPitch);

        // Bound pitch delta before quantization to prevent breaking GCD grid on bounds
        float minPitchDelta = -89.0f - currentPitch;
        float maxPitchDelta = 89.0f - currentPitch;
        float boundedPitchDelta = MathHelper.clamp(rawDeltaPitch, minPitchDelta, maxPitchDelta);

        // Single authoritative quantization point with residual error diffusion
        net.minecraft.util.math.Vec2f quantized = quantizer.quantizeDelta(rawDeltaYaw, boundedPitchDelta);

        float qPitch = quantized.y;
        float gcd = GCDUtil.getGCDValue();
        if (gcd > 0.00005f) {
            while (currentPitch + qPitch > 89.0f && qPitch > 0) {
                qPitch -= gcd;
            }
            while (currentPitch + qPitch < -89.0f && qPitch < 0) {
                qPitch += gcd;
            }
        }

        lastAppliedYawDelta = quantized.x;
        lastAppliedPitchDelta = qPitch;

        float yaw = currentYaw + quantized.x;
        float pitch = currentPitch + qPitch;
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);

        idleTicks(0);
        return new Rotation(mc.player).getDelta(targetRotation) < 1F;
    }

    public void stopRotation() {
        currentTask(RotationTask.IDLE);
        currentPriority(0);
        FreeLookStorage.setActive(false);
        quantizer.reset();
        lastAppliedYawDelta = 0.0f;
        lastAppliedPitchDelta = 0.0f;
    }

    public boolean isRotating() {
        return !currentTask.equals(RotationTask.IDLE);
    }

    public enum RotationTask {
        AIM,
        RESET,
        IDLE
    }
}
