package snill.client.client.modules.impl.combat.components.rotations.physics;

import net.minecraft.util.math.Vec2f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SecondOrderPhysicsControllerTest {

    private SecondOrderPhysicsController controller;

    @BeforeEach
    public void setUp() {
        // Deterministic controller without stochastic noise for mathematical proofs
        controller = new SecondOrderPhysicsController(22.0f, 0.98f, 720.0f, 480.0f, 5000.0f, 30000.0f, 0.14f, 0.0f);
    }

    @Test
    public void testNumericalStabilityAndConvergence() {
        float currentYaw = 0.0f;
        float currentPitch = 0.0f;
        float targetYaw = 45.0f;
        float targetPitch = 15.0f;
        float dt = 0.05f;

        float prevErrorYaw = Math.abs(targetYaw - currentYaw);

        for (int i = 0; i < 40; i++) {
            Vec2f step = controller.step(currentYaw, currentPitch, targetYaw, targetPitch, dt);
            assertTrue(Float.isFinite(step.x), "Step yaw must be finite");
            assertTrue(Float.isFinite(step.y), "Step pitch must be finite");

            currentYaw += step.x;
            currentPitch += step.y;

            // Reconcile velocity
            controller.reconcileAppliedDelta(step.x, step.y, dt);
        }

        float finalErrorYaw = Math.abs(targetYaw - currentYaw);
        float finalErrorPitch = Math.abs(targetPitch - currentPitch);

        // Sub-stepping guarantees stable settling without divergence
        assertTrue(finalErrorYaw < 0.2f, "Yaw should converge to target, error: " + finalErrorYaw);
        assertTrue(finalErrorPitch < 0.2f, "Pitch should converge to target, error: " + finalErrorPitch);
    }

    @Test
    public void testShortestArcAngleWrapping() {
        float currentYaw = 179.0f;
        float currentPitch = 0.0f;
        float targetYaw = -179.0f; // Shortest arc is +2 degrees, not -358
        float targetPitch = 0.0f;
        float dt = 0.05f;

        Vec2f step = controller.step(currentYaw, currentPitch, targetYaw, targetPitch, dt);
        assertTrue(step.x > 0, "Controller must take shortest arc (+2 deg) and move in positive direction, got: " + step.x);
        assertTrue(step.x <= 2.5f, "Step magnitude must be bounded by shortest arc, got: " + step.x);
    }

    @Test
    public void testInputSanitization() {
        // Test robustness against NaN, Infinity, negative frequencies
        controller.setNaturalFrequency(Float.NaN);
        assertEquals(20.0f, controller.getNaturalFrequency(), 0.01f);

        controller.setDampingRatio(-5.0f);
        assertTrue(controller.getDampingRatio() >= 0.1f);

        Vec2f nanResult = controller.step(Float.NaN, 0.0f, 45.0f, 0.0f, 0.05f);
        assertEquals(0.0f, nanResult.x);
        assertEquals(0.0f, nanResult.y);
    }

    @Test
    public void testVelocityReconciliation() {
        controller.reconcileAppliedDelta(10.0f, 5.0f, 0.05f);
        assertEquals(200.0f, controller.getVelocityYaw(), 0.1f);
        assertEquals(100.0f, controller.getVelocityPitch(), 0.1f);
    }
}
