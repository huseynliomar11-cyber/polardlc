package snill.client.client.modules.impl.combat.components.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import snill.client.api.QClient;
import snill.client.api.storages.implement.FreeLookStorage;
import snill.client.api.storages.implement.RotationStorage;
import snill.client.api.utils.rotate.MultipointUtils;
import snill.client.api.utils.rotate.Rotation;
import snill.client.api.utils.rotate.RotationUtils;
import snill.client.client.modules.impl.combat.Aura;
import snill.client.client.modules.impl.combat.components.RotationsSystem;
import snill.client.client.modules.impl.combat.components.gcd.GCDUtil;
import snill.client.client.modules.impl.combat.components.interpolation.BestPoint;
import ru.virtuoz.convert.Convert;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
@Convert(Convert.ConvertType.MUTATION)
public class SpookyTimeRotation extends RotationsSystem implements QClient {

    private final ThreadLocalRandom rnd = ThreadLocalRandom.current();

    private Vec3d stableAim;
    private Vec3d multipointTarget;
    private Vec3d aimMotion = Vec3d.ZERO;
    private boolean initialized;

    private float jerkYaw;
    private float jerkPitch;
    private int jerkCd;
    private float jerkTargetYaw;
    private float jerkTargetPitch;
    private int jerkTicks;
    private int multipointSwitchTicks;
    private float multipointPhase;

    private float aimOffYaw;
    private float aimOffPitch;
    private int tick;
    private int aimRefreshTicks;
    private float lastTargetYaw;
    private float lastTargetPitch;
    private float pitchDamp;
    private int postHitTicks;
    private float shakeAmplitude;
    private int shakeTicks;
    private float attackFlickYaw;
    private int attackFlickTicks;
    private int secondStrikeTicks;
    private float lastYawPos = 0.0F;
    private float lastPitchPos = 0.0F;
    private boolean hasLastRotation;

    private static final float MAX_YAW_SPEED = 45.0F;
    private static final float MAX_PITCH_SPEED = 20.0F;

    public void reset() {
        stableAim = null;
        multipointTarget = null;
        aimMotion = Vec3d.ZERO;
        jerkYaw = 0;
        jerkPitch = 0;
        jerkCd = 0;
        jerkTargetYaw = 0;
        jerkTargetPitch = 0;
        jerkTicks = 0;
        multipointSwitchTicks = 0;
        multipointPhase = 0;
        aimOffYaw = 0;
        aimOffPitch = 0;
        tick = 0;
        aimRefreshTicks = 0;
        initialized = false;
        lastTargetYaw = 0;
        lastTargetPitch = 0;
        pitchDamp = 0.28f;
        postHitTicks = 0;
        shakeAmplitude = 0;
        shakeTicks = 0;
        attackFlickYaw = 0;
        attackFlickTicks = 0;
        secondStrikeTicks = 0;
        hasLastRotation = false;
    }
    public void preserveLastCameraRotation() {
        if (!hasLastRotation || mc.player == null) return;

        mc.player.setYaw(lastYawPos);
        mc.player.setPitch(lastPitchPos);
        FreeLookStorage.setFreeYaw(lastYawPos);
        FreeLookStorage.setFreePitch(lastPitchPos);
    }

    private void rememberCurrentRotation() {
        lastYawPos = mc.player.getYaw();
        lastPitchPos = mc.player.getPitch();
        hasLastRotation = true;
    }

    public void onAttack() {
        jerkCd = 0;
        jerkYaw = 0;
        jerkPitch = 0;
        jerkTicks = 2 + rnd.nextInt(2);
        jerkTargetYaw = (rnd.nextFloat() - 0.5F) * 1.45F;
        jerkTargetPitch = (rnd.nextFloat() - 0.5F) * 0.65F;
        aimOffYaw *= 0.32f;
        aimOffPitch *= 0.32f;
        postHitTicks = 8 + rnd.nextInt(5);
        shakeAmplitude = 0.15f + rnd.nextFloat() * 0.2f;
        shakeTicks = 3 + rnd.nextInt(3);

        // Each hit gets one distinct follow-up: a sharp left/right flick or a
        // second swing on the next tick. Keeping the second swing delayed makes
        // the reaction visible to the rotation system before it is sent.
        int followUp = rnd.nextInt(3);
        if (followUp == 2) {
            secondStrikeTicks = 1;
        } else {
            attackFlickYaw = (followUp == 0 ? -1.0F : 1.0F) * rnd.nextFloat(22.0F, 34.0F);
            attackFlickTicks = 2;
        }
    }

    /** Returns true exactly once, one tick after a SpookyTime hit chose a double strike. */
    public boolean consumeSecondStrike() {
        if (secondStrikeTicks <= 0) return false;
        if (--secondStrikeTicks > 0) return false;
        return true;
    }

    @Override
    public void updateRotations(LivingEntity target) {
        if (mc.player == null || target == null) return;
        Vec3d eyePos = mc.player.getCameraPosVec(1.0F);
        if (target.getBoundingBox().contains(eyePos)) {
            Aura.adjYaw = 0;
            Aura.adjPitch = 0;
            RotationStorage.update(
                    new Rotation(mc.player.getYaw(), mc.player.getPitch()),
                    360, 360,
                    40, 35, 1,
                    1, Aura.clientLook.isState()
            );
            rememberCurrentRotation();
            return;
        }

        tick++;

        Vec3d lookVec = mc.player.getRotationVec(1.0F);
        Vec3d reachVec = eyePos.add(lookVec.multiply(999));

        Box box = getPredictedBox(target);

        Optional<Vec3d> hit = box.raycast(eyePos, reachVec);
        boolean inside = box.contains(eyePos);

        if (hit.isPresent() || inside) {
            Aura.adjYaw = MathHelper.clamp(Aura.adjYaw - rnd.nextFloat(0.005f, 0.02f), 0, 1);
            Aura.adjPitch = MathHelper.clamp(Aura.adjPitch - rnd.nextFloat(0.005f, 0.02f), 0, 1);
        } else {
            Aura.adjYaw = MathHelper.clamp(Aura.adjYaw + rnd.nextFloat(0.00009f, 0.009f), 0, 1);
            Aura.adjPitch = MathHelper.clamp(Aura.adjPitch + rnd.nextFloat(0.0009f, 0.009f), 0, 1);
        }

        if (postHitTicks > 0) {
            postHitTicks--;
            Aura.adjYaw *= 0.82f;
            Aura.adjPitch *= 0.82f;
        }

        if (shakeTicks > 0) shakeTicks--;

        double dist = eyePos.distanceTo(target.getPos());
        boolean close = dist < 2.8;

        updateMultipoint(target, box, dist, close);

        updateAimOffset();
        updateJerk(target);

        Vec2f angle = RotationUtils.getRotations(stableAim);

        float jitterYaw = (float) (Math.sin(tick * 1.7) * 0.04 + Math.cos(tick * 2.3) * 0.03);
        float jitterPitch = (float) (Math.sin(tick * 1.9 + 1) * 0.03 + Math.cos(tick * 2.7 + 2) * 0.02);

        float rawTargetYaw = angle.x + aimOffYaw + jitterYaw;
        float rawTargetPitch = MathHelper.clamp(angle.y + aimOffPitch + jitterPitch, -89.0F, 89.0F);

        if (!initialized) {
            lastTargetYaw = rawTargetYaw;
            lastTargetPitch = rawTargetPitch;
            initialized = true;
        }

        Aura.otvodkaYaw = (lastTargetYaw - rawTargetYaw) * 0.3f;
        Aura.otvodkaPitch = (lastTargetPitch - rawTargetPitch) * 0.25f;

        lastTargetYaw = rawTargetYaw;
        lastTargetPitch = rawTargetPitch;

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float diffYaw = MathHelper.wrapDegrees(rawTargetYaw - currentYaw);
        float diffPitch = rawTargetPitch - currentPitch;

        diffYaw = MathHelper.clamp(diffYaw, -MAX_YAW_SPEED, MAX_YAW_SPEED);
        diffPitch = MathHelper.clamp(diffPitch, -MAX_PITCH_SPEED, MAX_PITCH_SPEED);

        if (postHitTicks > 0) {
            diffYaw *= 0.7f;
            diffPitch *= 0.7f;
        }

        pitchDamp = MathHelper.lerp(0.14f, pitchDamp, 0.22f + rnd.nextFloat() * 0.06f);

        float shakeYaw = shakeTicks > 0 ? (rnd.nextFloat() - 0.5F) * shakeAmplitude : 0;
        float shakePitch = shakeTicks > 0 ? (rnd.nextFloat() - 0.5F) * shakeAmplitude : 0;

        float acquireFactor = MathHelper.clamp(Math.abs(diffYaw) / 80.0F, 0.38F, 1.0F);
        float newYaw = currentYaw + diffYaw * Aura.adjYaw * acquireFactor + jerkYaw + Aura.otvodkaYaw + shakeYaw;
        float newPitch = currentPitch + diffPitch * Aura.adjPitch * pitchDamp * acquireFactor + jerkPitch * 0.55F + Aura.otvodkaPitch + shakePitch;

        if (attackFlickTicks > 0) {
            newYaw += attackFlickYaw;
        }

        newPitch = MathHelper.clamp(newPitch, -89.0F, 89.0F);

        RotationStorage.update(
                new Rotation(newYaw, newPitch),
                360, 360,
                40, 35, 1,
                1, Aura.clientLook.isState()
        );
        if (attackFlickTicks > 0) {
            attackFlickTicks--;
            attackFlickYaw *= 0.34F;
        }
        rememberCurrentRotation();
    }

    private void updateAimOffset() {
        if (tick % (3 + rnd.nextInt(2)) != 0) {
            aimOffYaw *= 0.85F;
            aimOffPitch *= 0.87F;
            return;
        }

        float spread = rnd.nextFloat(0.12f, 0.28f);
        aimOffYaw += (rnd.nextFloat() - 0.5F) * spread;
        aimOffPitch += (rnd.nextFloat() - 0.5F) * spread * 0.4F;

        aimOffYaw = MathHelper.clamp(aimOffYaw, -0.3F, 0.3F);
        aimOffPitch = MathHelper.clamp(aimOffPitch, -0.15F, 0.15F);
    }

    private void updateMultipoint(LivingEntity target, Box box, double distance, boolean close) {
        if (multipointSwitchTicks-- <= 0 || multipointTarget == null) {
            Vec3d candidate = BestPoint.getMultipoint(target, distance + 1.6D);
            if (candidate == null) candidate = BestPoint.getPoint(target);
            if (candidate == null) candidate = MultipointUtils.getClosestPoint(target);
            if (candidate == null) candidate = box.getCenter();

            if (close) candidate = candidate.lerp(box.getCenter(), 0.32D);
            multipointTarget = shouldUseElytraPredict(target) ? getPredictedPoint(target, candidate) : candidate;
            multipointSwitchTicks = close ? 3 + rnd.nextInt(3) : 2 + rnd.nextInt(4);
        }

        if (stableAim == null) {
            stableAim = multipointTarget;
            aimMotion = Vec3d.ZERO;
            return;
        }

        // Spring interpolation removes teleports between BestPoint samples while
        // retaining a small, irregular trajectory through the hitbox.
        Vec3d displacement = multipointTarget.subtract(stableAim);
        double stiffness = close ? 0.16D : 0.24D;
        aimMotion = aimMotion.multiply(close ? 0.58D : 0.64D).add(displacement.multiply(stiffness));
        double maxStep = close ? 0.085D : 0.16D;
        if (aimMotion.length() > maxStep) aimMotion = aimMotion.normalize().multiply(maxStep);
        multipointPhase += rnd.nextFloat(0.18F, 0.31F);
        double wobble = close ? 0.006D : 0.012D;
        Vec3d next = stableAim.add(aimMotion).add(Math.sin(multipointPhase) * wobble, Math.cos(multipointPhase * 1.37F) * wobble * 0.65D, Math.sin(multipointPhase * 0.71F) * wobble);
        stableAim = clampToBox(next, box);
    }

    private Vec3d clampToBox(Vec3d point, Box box) {
        double margin = 0.015D;
        return new Vec3d(
                MathHelper.clamp(point.x, box.minX + margin, box.maxX - margin),
                MathHelper.clamp(point.y, box.minY + margin, box.maxY - margin),
                MathHelper.clamp(point.z, box.minZ + margin, box.maxZ - margin)
        );
    }

    private void updateJerk(LivingEntity target) {
        if (jerkTicks > 0) {
            jerkTicks--;
            jerkYaw += (jerkTargetYaw - jerkYaw) * 0.72F;
            jerkPitch += (jerkTargetPitch - jerkPitch) * 0.68F;
            return;
        }
        if (jerkCd > 0) {
            jerkCd--;
            jerkYaw *= 0.72F;
            jerkPitch *= 0.75F;
            return;
        }

        float yawDiff = Math.abs(MathHelper.wrapDegrees(
                RotationUtils.getRotations(stableAim).x - mc.player.getYaw()));
        float pitchDiff = Math.abs(
                RotationUtils.getRotations(stableAim).y - mc.player.getPitch());

        double dist = mc.player.getEyePos().distanceTo(target.getPos());
        float mul = dist < 2.8 ? 0.7F : 1.1F;

        if (yawDiff > 30.0F && rnd.nextFloat() > 0.35F) {
            float direction = Math.signum(MathHelper.wrapDegrees(RotationUtils.getRotations(stableAim).x - mc.player.getYaw()));
            jerkTargetYaw = direction * rnd.nextFloat(1.1F, 2.8F) * mul;
            jerkTargetPitch = (rnd.nextFloat() - 0.5F) * 0.9F * mul;
            jerkTicks = 2 + rnd.nextInt(2);
            jerkCd = 5 + rnd.nextInt(4);
        } else if (yawDiff > 6.0F && rnd.nextFloat() > 0.45F) {
            float gcd = GCDUtil.getGCDValue();
            float amp = (gcd > 0.0F ? gcd * rnd.nextFloat(1.5F, 2.8F) : 1.3F) * mul;
            float sign = Math.signum(MathHelper.wrapDegrees(
                    RotationUtils.getRotations(target.getBoundingBox().getCenter()).x - mc.player.getYaw()));
            if (sign == 0.0F) sign = rnd.nextBoolean() ? 1.0F : -1.0F;
            jerkTargetYaw = sign * amp;
            jerkTargetPitch = (rnd.nextFloat() - 0.5F) * amp * 0.32F;
            jerkTicks = 1 + rnd.nextInt(2);
            jerkCd = 3 + rnd.nextInt(3);
        }

        if (pitchDiff > 6.0F && rnd.nextFloat() > 0.6F) {
            jerkPitch = (rnd.nextFloat() - 0.5F) * 1.4F * mul;
            jerkCd = Math.max(jerkCd, 5 + rnd.nextInt(3));
        }
    }
}
