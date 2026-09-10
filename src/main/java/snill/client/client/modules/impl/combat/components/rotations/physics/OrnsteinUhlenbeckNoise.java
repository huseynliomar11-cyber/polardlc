package snill.client.client.modules.impl.combat.components.rotations.physics;

import java.util.Random;

/**
 * Continuous mean-reverting Ornstein-Uhlenbeck stochastic process.
 * Used for realistic neuromuscular motor noise added to angular acceleration.
 * Formula: n_(k+1) = n_k * exp(-dt/tau) + sigma * sqrt(1 - exp(-2*dt/tau)) * xi
 */
public class OrnsteinUhlenbeckNoise {
    private final float tau;   // Correlation time constant in seconds
    private final float sigma; // Noise volatility / standard deviation
    private float state;
    private final Random random;

    public OrnsteinUhlenbeckNoise(float tau, float sigma) {
        this.tau = Math.max(0.001f, tau);
        this.sigma = sigma;
        this.state = 0.0f;
        this.random = new Random();
    }

    public void reset() {
        this.state = 0.0f;
    }

    /**
     * Advances the OU process by dt seconds and returns current noise value.
     */
    public float update(float dt) {
        if (dt <= 0.0001f) dt = 0.05f;
        float decay = (float) Math.exp(-dt / tau);
        float varianceFactor = (float) Math.sqrt(Math.max(0.0, 1.0 - Math.exp(-2.0 * dt / tau)));
        float xi = (float) random.nextGaussian();
        state = state * decay + sigma * varianceFactor * xi;
        return state;
    }

    public float getState() {
        return state;
    }
}
