package snill.client.client.modules.impl.combat.components.rotations.physics;

import net.minecraft.util.math.Vec2f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import snill.client.client.modules.impl.combat.components.gcd.GCDUtil;

import static org.junit.jupiter.api.Assertions.*;

public class InputQuantizerTest {

    private InputQuantizer quantizer;

    @BeforeEach
    public void setUp() {
        quantizer = new InputQuantizer();
    }

    @Test
    public void testQuantizationToGcdGrid() {
        float gcd = GCDUtil.getGCDValue();
        assertTrue(gcd > 0.0001f, "Default GCD should be positive");

        // An arbitrary delta
        float inputYaw = 1.2345f;
        float inputPitch = -0.7891f;

        Vec2f quantized = quantizer.quantizeDelta(inputYaw, inputPitch);

        // Quantized value must be an integer multiple of GCD within float epsilon
        float yawSteps = quantized.x / gcd;
        float pitchSteps = quantized.y / gcd;

        assertEquals(Math.round(yawSteps), yawSteps, 1e-4f, "Yaw output must be an exact multiple of GCD");
        assertEquals(Math.round(pitchSteps), pitchSteps, 1e-4f, "Pitch output must be an exact multiple of GCD");
    }

    @Test
    public void testResidualAccumulation() {
        float gcd = GCDUtil.getGCDValue();
        float microDelta = gcd * 0.25f; // Smaller than half GCD, so first step should round to 0

        Vec2f step1 = quantizer.quantizeDelta(microDelta, 0.0f);
        assertEquals(0.0f, step1.x, 1e-4f, "First micro delta should be retained in residual");
        assertTrue(Math.abs(quantizer.getResidualYaw() - microDelta) < 1e-4f);

        // Second step: total = 0.5 * gcd -> rounds to gcd
        Vec2f step2 = quantizer.quantizeDelta(microDelta, 0.0f);
        float step2Steps = step2.x / gcd;
        assertEquals(1.0f, step2Steps, 1e-4f, "Accumulated micro deltas should fire once reaching threshold");
    }

    @Test
    public void testSanitizationAgainstNonFinite() {
        Vec2f nanResult = quantizer.quantizeDelta(Float.NaN, Float.POSITIVE_INFINITY);
        assertEquals(0.0f, nanResult.x);
        assertEquals(0.0f, nanResult.y);
    }

    @Test
    public void testReset() {
        float gcd = GCDUtil.getGCDValue();
        quantizer.quantizeDelta(gcd * 0.3f, gcd * 0.3f);
        assertTrue(Math.abs(quantizer.getResidualYaw()) > 0.001f);

        quantizer.reset();
        assertEquals(0.0f, quantizer.getResidualYaw());
        assertEquals(0.0f, quantizer.getResidualPitch());
    }
}
