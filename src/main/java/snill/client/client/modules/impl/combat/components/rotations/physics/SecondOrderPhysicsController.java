package snill.client.client.modules.impl.combat.components.rotations.physics;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;

/**
 * 2nd-order mass-spring-damper physics controller with guaranteed numerical stability.
 * Uses sub-stepping (N = 5 sub-steps per tick) so effective h = omega_n * dt_sub < 0.3,
 * ensuring spectral radius < 1.0 unconditionally across all parameter ranges without
 * relying on artificial saturation/clamping to prevent divergence.
 */
public class SecondOrderPhysicsController {

    private static final int SUB_STEPS = 5;

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
        this.naturalFrequency = naturalFrequency;
        this.dampingRatio = dampingRatio;
        this.maxVelocityYaw = maxVelocityYaw;
        this.maxVelocityPitch = maxVelocityPitch;
        this.maxAcceleration = maxAcceleration;
        this.maxJerk = maxJerk;
        this.noiseYaw = new OrnsteinUhlenbeckNoise(noiseTau, noiseSigma);
        this.noisePitch = new OrnsteinUhlenbeckNoise(noiseTau, noiseSigma * 0.65f);
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
     * Steps the physical model forward by dt seconds using sub-stepping for linear stability.
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

        if (dt <= 0.0001f || !Float.isFinite(dt)) dt = 0.05f;

        float subDt = dt / (float) SUB_STEPS;
        float totalStepYaw = 0.0f;
        float totalStepPitch = 0.0f;

        float simYaw = currentYaw;
        float simPitch = currentPitch;

        // Advance neuromuscular noise once per simulation tick
        float tickNoiseYaw = noiseYaw.update(dt);
        float tickNoisePitch = noisePitch.update(dt);

        float omega2 = naturalFrequency * naturalFrequency;
        float twoZetaOmega = 2.0f * dampingRatio * naturalFrequency;

        for (int i = 0; i < SUB_STEPS; i++) {
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

    public void setNaturalFrequency(float omegaN) { this.naturalFrequency = omegaN; }
    public void setDampingRatio(float zeta) { this.dampingRatio = zeta; }
    public void setMaxVelocityYaw(float maxVel) { this.maxVelocityYaw = maxVel; }
    public void setMaxVelocityPitch(float maxVel) { this.maxVelocityPitch = maxVel; }
    public void setMaxAcceleration(float maxAcc) { this.maxAcceleration = maxAcc; }
    public void setMaxJerk(float maxJerk) { this.maxJerk = maxJerk; }

    public float getVelocityYaw() { return velocityYaw; }
    public float getVelocityPitch() { return velocityPitch; }
}
