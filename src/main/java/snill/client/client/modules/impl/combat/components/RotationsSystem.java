package snill.client.client.modules.impl.combat.components;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import snill.client.api.QClient;
import snill.client.client.modules.impl.combat.ElytraTarget;
import snill.client.client.modules.impl.combat.components.gcd.GCDUtil;
import ru.virtuoz.convert.Convert;

@Convert(Convert.ConvertType.MUTATION)
public abstract class RotationsSystem implements QClient {

    public Vec2f rotate = Vec2f.ZERO;

    public abstract void updateRotations(LivingEntity target);

    public static Vec2f correctRotation(float yaw, float pitch) {
        if (mc.player == null) return new Vec2f(yaw, pitch);
        if ((yaw == -90 && pitch == 90) || yaw == -180) return new Vec2f(mc.player.getYaw(), mc.player.getPitch());

        float gcd = GCDUtil.getGCDValue();
        if (gcd <= 0.0001f) return new Vec2f(yaw, pitch);

        float deltaYaw = yaw - mc.player.getYaw();
        float deltaPitch = pitch - mc.player.getPitch();
        float fixedYaw = mc.player.getYaw() + Math.round(deltaYaw / gcd) * gcd;
        float fixedPitch = mc.player.getPitch() + Math.round(deltaPitch / gcd) * gcd;

        return new Vec2f(fixedYaw, net.minecraft.util.math.MathHelper.clamp(fixedPitch, -89.9f, 89.9f));
    }

    protected boolean shouldUseElytraPredict(LivingEntity target) {
        ElytraTarget elytraTarget = ElytraTarget.INSTANCE;
        return mc.player != null
                && target != null
                && mc.player.isGliding()
                && target.isGliding()
                && elytraTarget != null
                && elytraTarget.isPredictionActive()
                && !elytraTarget.isCakeWorldMode();
    }

    protected int getElytraPredictTicks() {
        ElytraTarget elytraTarget = ElytraTarget.INSTANCE;
        if (elytraTarget == null || !elytraTarget.isPredictionActive()) {
            return 0;
        }
        return Math.max(0, elytraTarget.getForwardTicks());
    }

    protected Vec3d getPredictedPoint(LivingEntity target, Vec3d point) {
        if (!shouldUseElytraPredict(target)) {
            return point;
        }

        ElytraTarget elytraTarget = ElytraTarget.INSTANCE;
        return elytraTarget != null ? elytraTarget.getPredictedPoint(target, point) : point;
    }

    protected Box getPredictedBox(LivingEntity target) {
        Box box = target.getBoundingBox();
        if (!shouldUseElytraPredict(target)) {
            return box;
        }
        Vec3d currentCenter = box.getCenter();
        Vec3d predictedCenter = getPredictedPoint(target, currentCenter);
        return box.offset(predictedCenter.subtract(currentCenter));
    }
}
