package snill.client.client.modules.impl.combat.components.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import snill.client.api.QClient;
import snill.client.client.modules.impl.combat.components.RotationsSystem;
import snill.client.client.modules.impl.combat.components.gcd.GCDUtil;

public class PredictRots extends RotationsSystem implements QClient {

    
    public Vec2f rotating(Vec2f rotation, LivingEntity target) {
        Vec3d vec = calcPointed(target);
        float rawYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(vec.z, vec.x)) - 90.0D);
        float rawPitch = (float) MathHelper.wrapDegrees(Math.toDegrees(-Math.atan2(vec.y, Math.hypot(vec.x, vec.z))));
        float yawDelta = MathHelper.wrapDegrees(rawYaw - rotation.x);
        float pitchDelta = MathHelper.wrapDegrees(rawPitch - rotation.y);
        if (Math.abs(yawDelta) > 180.0F) {
            yawDelta -= Math.signum(yawDelta) * 360.0F;
        }

        float additionYaw = MathHelper.clamp(yawDelta, -180.0F, 180.0F);
        float additionPitch = MathHelper.clamp(pitchDelta, -90.0F, 90.0F);
        float yaw = rotation.x + additionYaw;
        float pitch = rotation.y + additionPitch;

        return new Vec2f(yaw, MathHelper.clamp(pitch, -89.9F, 89.9F));
    }

    
    private Vec3d calcPointed(LivingEntity target) {
        if (target != null) {
            Vec3d vecPosition = getPredictedPoint(target, target.getBoundingBox().getCenter());

            return new Vec3d(vecPosition.getX() - mc.player.getX(), vecPosition.getY() - mc.player.getY(), vecPosition.getZ() - mc.player.getZ());
        }
        return Vec3d.ZERO;
    }

    @Override
    public void updateRotations(LivingEntity entity) {

    }
}
