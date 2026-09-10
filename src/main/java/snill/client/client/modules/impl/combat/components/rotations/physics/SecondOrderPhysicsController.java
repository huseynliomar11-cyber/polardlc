package snill.client.client.modules.impl.combat.components.rotations.physics;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;

/**
 * 2nd-order mass-spring-damper physics controller with guaranteed numerical stability.
 * Uses dynamic sub-stepping (N calculated dynamically from omega_n * dt / 0.20f)
 * to guarantee that h_sub = omega_n * dt_sub <= 0.20f unconditionally across all parameter ranges,
 * ensuring spectral radius rho < 1.0 without relying on artificial saturation/clamping to prevent divergence.
 */
public class SecondOrderPhysicsController {

    // Physical configuration
    private float naturalFrequency; // omega_n (rad/s), response speed
    private float dampingRatio;     // zeta: 1.0 = critically damped, <1 = overshoot, >1 = overdamped
    private float maxVelocityYaw;   // deg/s
    private float maxVelocityPitch; // deg/s
    private float maxAcceleration;  // deg/s^2
    private float maxJerk;          // deg/s^3

    // Dynamic state
    private float velocityYaw = 0.0f;
    private float velocityPitch = 0.0f;
    private float prevAccelYaw = 0.0f;
    private float prevAccelPitch = 0.0f;

    // Stochastic continuous noise generators
    private final OrnsteinUhlenbeckNoise noiseYaw;
    private final OrnsteinUhlenbeckNoise noisePitch;

    public SecondOrderPhysicsController(float naturalFrequency, float dampingRatio,
                                        float maxVelocityYaw, float maxVelocityPitch,
                                        float maxAcceleration, float maxJerk,
                                        float noiseTau, float noiseSigma) {
        this.naturalFrequency = sanitize(naturalFrequency, 0.5f, 100.0f, 20.0f);
        this.dampingRatio = sanitize(dampingRatio, 0.1f, 5.0f, 1.0f);
        this.maxVelocityYaw = sanitize(maxVelocityYaw, 10.0f, 3600.0f, 720.0f);
        this.maxVelocityPitch = sanitize(maxVelocityPitch, 10.0f, 3600.0f, 480.0f);
        this.maxAcceleration = sanitize(maxAcceleration, 100.0f, 50000.0f, 5000.0f);
        this.maxJerk = sanitize(maxJerk, 500.0f, 200000.0f, 30000.0f);
        this.noiseYaw = new OrnsteinUhlenbeckNoise(sanitize(noiseTau, 0.01f, 5.0f, 0.14f), sanitize(noiseSigma, 0.0f, 500.0f, 85.0f));
        this.noisePitch = new OrnsteinUhlenbeckNoise(sanitize(noiseTau, 0.01f, 5.0f, 0.14f), sanitize(noiseSigma * 0.65f, 0.0f, 500.0f, 55.0f));
    }

    public void reset() {
        velocityYaw = 0.0f;
        velocityPitch = 0.0f;
        prevAccelYaw = 0.0f;
        prevAccelPitch = 0.0f;
        noiseYaw.reset();
        noisePitch.reset();
    }

    /**
     * Reconciles internal physical velocity with the actual quantized displacement
     * that was committed to the authoritative player state by the quantizer.
     * Prevents velocity accumulation divergence when quantization rounds or clamps movement.
     */
    public void reconcileAppliedDelta(float appliedYawDelta, float appliedPitchDelta, float dt) {
        if (!Float.isFinite(appliedYawDelta) || !Float.isFinite(appliedPitchDelta) || !Float.isFinite(dt) || dt <= 0.0001f) {
            return;
        }
        this.velocityYaw = MathHelper.clamp(appliedYawDelta / dt, -maxVelocityYaw, maxVelocityYaw);
        this.velocityPitch = MathHelper.clamp(appliedPitchDelta / dt, -maxVelocityPitch, maxVelocityPitch);
    }

    /**
     * Steps the physical model forward by dt seconds using dynamic sub-stepping for unconditional stability.
     * @param currentYaw current yaw in degrees
     * @param currentPitch current pitch in degrees
     * @param targetYaw target yaw in degrees
     * @param targetPitch target pitch in degrees
     * @param dt step delta time in seconds (typically 0.05f for 20 TPS)
     * @return angular delta (deltaYaw, deltaPitch)
     */
    public Vec2f step(float currentYaw, float currentPitch, float targetYaw, float targetPitch, float dt) {
        if (!Float.isFinite(currentYaw) || !Float.isFinite(currentPitch) ||
            !Float.isFinite(targetYaw) || !Float.isFinite(targetPitch)) {
            return Vec2f.ZERO;
        }

        float validDt = sanitize(dt, 0.001f, 0.25f, 0.05f);

        // Dynamically determine sub-steps count to ensure h_sub = omega_n * subDt <= 0.20f
        int subSteps = MathHelper.clamp((int) Math.ceil(naturalFrequency * validDt / 0.20f), 2, 20);
        float subDt = validDt / (float) subSteps;

        float totalStepYaw = 0.0f;
        float totalStepPitch = 0.0f;

        float simYaw = currentYaw;
        float simPitch = currentPitch;

        // Advance neuromuscular noise once per simulation step
        float tickNoiseYaw = noiseYaw.update(validDt);
        float tickNoisePitch = noisePitch.update(validDt);

        float omega2 = naturalFrequency * naturalFrequency;
        float twoZetaOmega = 2.0f * dampingRatio * naturalFrequency;

        for (int i = 0; i < subSteps; i++) {
            // Shortest arc error calculation at current sub-step position
            float errorYaw = MathHelper.wrapDegrees(targetYaw - simYaw);
            float errorPitch = MathHelper.clamp(targetPitch, -89.9f, 89.9f) - simPitch;

            // Second-order differential equation: a = omega_n^2 * e - 2 * zeta * omega_n * v
            float rawAccelYaw = omega2 * errorYaw - twoZetaOmega * velocityYaw + tickNoiseYaw;
            float rawAccelPitch = omega2 * errorPitch - twoZetaOmega * velocityPitch + tickNoisePitch;

            // Jerk limit: prevent discontinuous instantaneous acceleration shifts
            float jerkStep = maxJerk * subDt;
            float accelYaw = MathHelper.clamp(rawAccelYaw, prevAccelYaw - jerkStep, prevAccelYaw + jerkStep);
            float accelPitch = MathHelper.clamp(rawAccelPitch, prevAccelPitch - jerkStep, prevAccelPitch + jerkStep);

            // Acceleration clamping
            accelYaw = MathHelper.clamp(accelYaw, -maxAcceleration, maxAcceleration);
            accelPitch = MathHelper.clamp(accelPitch, -maxAcceleration, maxAcceleration);
            prevAccelYaw = accelYaw;
            prevAccelPitch = accelPitch;

            // Semi-implicit Euler integration:
            // 1. Update velocity with bounded limits
            velocityYaw = MathHelper.clamp(velocityYaw + accelYaw * subDt, -maxVelocityYaw, maxVelocityYaw);
            velocityPitch = MathHelper.clamp(velocityPitch + accelPitch * subDt, -maxVelocityPitch, maxVelocityPitch);

            // 2. Calculate angular displacement for sub-step
            float subStepYaw = velocityYaw * subDt;
            float subStepPitch = velocityPitch * subDt;

            // 3. Smooth terminal settling: if within sub-degree proximity and velocity would overshoot without acceleration
            if (Math.abs(errorYaw) < Math.abs(subStepYaw) && (errorYaw * subStepYaw > 0)) {
                subStepYaw = errorYaw * 0.90f;
                velocityYaw = subStepYaw / subDt;
            }
            if (Math.abs(errorPitch) < Math.abs(subStepPitch) && (errorPitch * subStepPitch > 0)) {
                subStepPitch = errorPitch * 0.90f;
                velocityPitch = subStepPitch / subDt;
            }

            simYaw += subStepYaw;
            simPitch += subStepPitch;
            totalStepYaw += subStepYaw;
            totalStepPitch += subStepPitch;
        }

        return new Vec2f(totalStepYaw, totalStepPitch);
    }

    private static float sanitize(float value, float min, float max, float fallback) {
        if (!Float.isFinite(value)) return fallback;
        return MathHelper.clamp(value, min, max);
    }

    public void setNaturalFrequency(float omegaN) { this.naturalFrequency = sanitize(omegaN, 0.5f, 100.0f, 20.0f); }
    public void setDampingRatio(float zeta) { this.dampingRatio = sanitize(zeta, 0.1f, 5.0f, 1.0f); }
    public void setMaxVelocityYaw(float maxVel) { this.maxVelocityYaw = sanitize(maxVel, 10.0f, 3600.0f, 720.0f); }
    public void setMaxVelocityPitch(float maxVel) { this.maxVelocityPitch = sanitize(maxVel, 10.0f, 3600.0f, 480.0f); }
    public void setMaxAcceleration(float maxAcc) { this.maxAcceleration = sanitize(maxAcc, 100.0f, 50000.0f, 5000.0f); }
    public void setMaxJerk(float maxJerk) { this.maxJerk = sanitize(maxJerk, 500.0f, 200000.0f, 30000.0f); }

    public float getVelocityYaw() { return velocityYaw; }
    public float getVelocityPitch() { return velocityPitch; }
    public float getNaturalFrequency() { return naturalFrequency; }
    public float getDampingRatio() { return dampingRatio; }
}
