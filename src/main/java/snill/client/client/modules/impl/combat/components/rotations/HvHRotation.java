package snill.client.client.modules.impl.combat.components.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import snill.client.api.QClient;
import snill.client.api.storages.implement.RotationStorage;
import snill.client.api.utils.rotate.Rotation;
import snill.client.api.utils.rotate.RotationUtils;
import snill.client.client.modules.impl.combat.Aura;
import snill.client.client.modules.impl.combat.components.RotationsSystem;
import snill.client.client.modules.impl.combat.components.gcd.GCDUtil;
import snill.client.client.modules.impl.combat.components.interpolation.BestPoint;

public class HvHRotation extends RotationsSystem implements QClient {

    private final Aura aura;

    public HvHRotation(Aura aura) {
        this.aura = aura;
    }

    public void reset() {
    }

    public void onAttack() {
    }

    @Override
    public void updateRotations(LivingEntity target) {
        if (mc.player == null || target == null) return;

        // In HvH mode, lock directly onto the closest point on the hitbox
        // to ensure reach distance is minimized and strikes land at max distance
        Vec3d aimPoint = BestPoint.getNearestPoint(target);
        if (aimPoint == null) {
            aimPoint = target.getBoundingBox().getCenter();
        }

        if (shouldUseElytraPredict(target)) {
            aimPoint = getPredictedPoint(target, aimPoint);
        }

        Vec2f targetRot = RotationUtils.getRotations(aimPoint);
        float newYaw = targetRot.x;
        float newPitch = MathHelper.clamp(targetRot.y, -89.0F, 89.0F);

        Rotation rot = new Rotation(newYaw, newPitch);
        // Instant 360 snap-lock in HvH mode
        RotationStorage.update(rot, 360.0F, 360.0F, 360.0F, 360.0F, 0, 10, Aura.clientLook.isState());

        rotate = new Vec2f(rot.getYaw(), rot.getPitch());
    }
}
