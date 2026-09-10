package snill.client.client.modules.impl.combat.components.rotations.physics;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import snill.client.client.modules.impl.combat.components.gcd.GCDUtil;

/**
 * Single-point GCD input quantizer with residual error diffusion.
 * Ensures all angular deltas conform strictly to client mouse sensitivity steps
 * without discarding sub-GCD micro movements.
 *
 * Formulas:
 *   x_k = deltaTheta_k + r_k
 *   q_k = round(x_k / gcd) * gcd
 *   r_(k+1) = x_k - q_k  (|r| <= 0.5 * gcd)
 */
public class InputQuantizer {

    private float residualYaw = 0.0f;
    private float residualPitch = 0.0f;
    private float lastGcd = 0.0f;

    public void reset() {
        residualYaw = 0.0f;
        residualPitch = 0.0f;
        lastGcd = 0.0f;
    }

    /**
     * Quantizes an angular delta using the client's current mouse sensitivity GCD.
     * Small deltas accumulate across frames and fire when reaching threshold,
     * maintaining smooth tracking without sensitivity grid violations.
     *
     * @param deltaYaw desired yaw change in degrees
     * @param deltaPitch desired pitch change in degrees
     * @return quantized (qYaw, qPitch) delta
     */
    public Vec2f quantizeDelta(float deltaYaw, float deltaPitch) {
        if (!Float.isFinite(deltaYaw) || !Float.isFinite(deltaPitch)) {
            return Vec2f.ZERO;
        }

        float gcd = GCDUtil.getGCDValue();
        if (gcd <= 0.00005f || !Float.isFinite(gcd)) {
            return new Vec2f(deltaYaw, deltaPitch);
        }

        // If sensitivity / GCD grid changed, reset residuals to prevent step discrepancies
        if (Math.abs(gcd - lastGcd) > 0.00001f) {
            residualYaw = 0.0f;
            residualPitch = 0.0f;
            lastGcd = gcd;
        }

        // Add residual from previous step
        float xYaw = deltaYaw + residualYaw;
        float xPitch = deltaPitch + residualPitch;

        // Quantize to integer multiple of sensitivity step
        float qYaw = Math.round(xYaw / gcd) * gcd;
        float qPitch = Math.round(xPitch / gcd) * gcd;

        // Save remainder for next frame (bounded strictly by [-0.5 * gcd, 0.5 * gcd])
        residualYaw = MathHelper.clamp(xYaw - qYaw, -0.5f * gcd, 0.5f * gcd);
        residualPitch = MathHelper.clamp(xPitch - qPitch, -0.5f * gcd, 0.5f * gcd);

        return new Vec2f(qYaw, qPitch);
    }

    public float getResidualYaw() {
        return residualYaw;
    }

    public float getResidualPitch() {
        return residualPitch;
    }
}
