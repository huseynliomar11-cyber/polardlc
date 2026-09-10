package snill.client.client.modules.impl.combat.components.gcd;

import snill.client.api.QClient;

public class GCDUtil implements QClient {

    public static float getFixedRotation(float rot) {
        float gcd = getGCDValue();
        if (gcd <= 0.0001F) return rot;
        return Math.round(rot / gcd) * gcd;
    }

    public static float getGCDValue() {
        return (float) (getGCD() * 0.15D);
    }

    public static float getGCD() {
        double sens = (mc != null && mc.options != null && mc.options.getMouseSensitivity() != null)
                ? mc.options.getMouseSensitivity().getValue()
                : 0.5D;
        double f = sens * 0.6000000238418579D + 0.20000000298023224D;
        return (float) (f * f * f * 8.0D);
    }

    public static float getDeltaMouse(float delta) {
        float gcd = getGCDValue();
        if (gcd <= 0.0001F) return delta;
        return Math.round(delta / gcd);
    }
}