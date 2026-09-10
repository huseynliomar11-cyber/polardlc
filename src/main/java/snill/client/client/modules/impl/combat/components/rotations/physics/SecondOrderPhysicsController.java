package snill.client.client.modules.impl.combat.components.rotations.physics;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;

/**
 * 2nd-order mass-spring-damper physics controller for smooth, natural camera tracking.
 * Features:
 *  - Continuous angular acceleration modeling (theta'' = omega_n^2 * e - 2 * zeta * omega_n * theta')
 *  - Semi-implicit Euler integration
 *  - Jerk limiting (bounded derivative of acceleration)
 *  - Ornstein-Uhlenbeck stochastic neuromuscular noise applied to acceleration
 *  - Shortest angular path wrapping
 */
public class SecondOrderPhysicsController {

    // Tuning parameters
    private float naturalFrequency; // omega_n (rad/s), response speed
    private float dampingRatio;     // zeta: 1.0 = critically damped, <1 = slight overshoot, >1 = overdamped
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
     * Steps the physical model forward by dt seconds.
     * @param currentYaw current yaw in degrees
     * @param currentPitch current pitch in degrees
     * @param targetYaw target yaw in degrees
     * @param targetPitch target pitch in degrees
     * @param dt step delta time in seconds (typically 0.05f for 20 TPS)
     * @return angular delta (deltaYaw, deltaPitch)
     */
    public Vec2f step(float currentYaw, float currentPitch, float targetYaw, float targetPitch, float dt) {
        if (dt <= 0.0001f) dt = 0.05f;

        // Shortest arc error calculation
        float errorYaw = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float errorPitch = MathHelper.clamp(targetPitch, -89.9f, 89.9f) - currentPitch;

        // Second-order differential equation: a = omega_n^2 * e - 2 * zeta * omega_n * v
        float omega2 = naturalFrequency * naturalFrequency;
        float twoZetaOmega = 2.0f * dampingRatio * naturalFrequency;

        float rawAccelYaw = omega2 * errorYaw - twoZetaOmega * velocityYaw;
        float rawAccelPitch = omega2 * errorPitch - twoZetaOmega * velocityPitch;

        // Add organic neuromuscular noise to acceleration (NOT to angle directly)
        rawAccelYaw += noiseYaw.update(dt);
        rawAccelPitch += noisePitch.update(dt);

        // Jerk limit: prevent discontinuous instantaneous acceleration shifts
        float jerkStep = maxJerk * dt;
        float accelYaw = MathHelper.clamp(rawAccelYaw, prevAccelYaw - jerkStep, prevAccelYaw + jerkStep);
        float accelPitch = MathHelper.clamp(rawAccelPitch, prevAccelPitch - jerkStep, prevAccelPitch + jerkStep);

        // Acceleration clamping
        accelYaw = MathHelper.clamp(accelYaw, -maxAcceleration, maxAcceleration);
        accelPitch = MathHelper.clamp(accelPitch, -maxAcceleration, maxAcceleration);
        prevAccelYaw = accelYaw;
        prevAccelPitch = accelPitch;

        // Semi-implicit Euler integration:
        // 1. Update velocity with bounded limits
        velocityYaw = MathHelper.clamp(velocityYaw + accelYaw * dt, -maxVelocityYaw, maxVelocityYaw);
        velocityPitch = MathHelper.clamp(velocityPitch + accelPitch * dt, -maxVelocityPitch, maxVelocityPitch);

        // 2. Calculate angular displacement
        float stepYaw = velocityYaw * dt;
        float stepPitch = velocityPitch * dt;

        // 3. Smooth terminal settling: if within sub-degree proximity and velocity would overshoot without acceleration
        if (Math.abs(errorYaw) < Math.abs(stepYaw) && (errorYaw * stepYaw > 0)) {
            stepYaw = errorYaw * 0.90f;
            velocityYaw = stepYaw / dt;
        }
        if (Math.abs(errorPitch) < Math.abs(stepPitch) && (errorPitch * stepPitch > 0)) {
            stepPitch = errorPitch * 0.90f;
            velocityPitch = stepPitch / dt;
        }

        return new Vec2f(stepYaw, stepPitch);
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
