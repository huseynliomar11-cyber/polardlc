package snill.client.client.modules.impl.movement;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.events.implement.EventUpdatePost;
import snill.client.client.modules.Module;
import snill.client.client.modules.impl.combat.Aura;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Speed extends Module {
    public static Speed INSTANCE = new Speed();

    public final ModeSetting mode = new ModeSetting("Режим", "Collision", "Collision", "GrimSpeed");
    public final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", true).visible(() -> mode.is("Collision"));
    public final BooleanSetting requireMoving = new BooleanSetting("Требовать движение", true).visible(() -> mode.is("Collision"));
    public final BooleanSetting pauseInLiquids = new BooleanSetting("Пауза в жидкости", false).visible(() -> mode.is("GrimSpeed"));
    public final BooleanSetting pauseWhileSneaking = new BooleanSetting("Пауза при приседании", false).visible(() -> mode.is("GrimSpeed"));
    public final FloatSetting speedFactor = new FloatSetting("Скорость", 8.0F, 1.0F, 15.0F, 0.1F).visible(() -> mode.is("GrimSpeed"));
    public final FloatSetting distance = new FloatSetting("Дистанция", 3.0F, 0.5F, 5.0F, 0.1F).visible(() -> mode.is("GrimSpeed"));
    public final BooleanSetting bypassDistance = new BooleanSetting("Bypass distance", false).visible(() -> mode.is("GrimSpeed"));
    public final FloatSetting bypassDistanceValue = new FloatSetting("Дистанция байпасса", 4.0F, 1.0F, 6.0F, 0.1F).visible(() -> mode.is("GrimSpeed") && bypassDistance.isState());
    public final FloatSetting bypassAngle = new FloatSetting("Угол спиной", 120.0F, 10.0F, 180.0F, 5.0F).visible(() -> mode.is("GrimSpeed") && bypassDistance.isState());
    public final BooleanSetting waterDistance = new BooleanSetting("Дистанция в воде", false).visible(() -> mode.is("GrimSpeed"));
    public final FloatSetting waterDistanceValue = new FloatSetting("Дистанция вода", 2.0F, 0.5F, 6.0F, 0.1F).visible(() -> mode.is("GrimSpeed") && waterDistance.isState());
    public final BooleanSetting elytraDistance = new BooleanSetting("Дистанция на элитре", false).visible(() -> mode.is("GrimSpeed"));
    public final FloatSetting elytraDistanceValue = new FloatSetting("Дистанция элитра", 5.0F, 0.5F, 10.0F, 0.1F).visible(() -> mode.is("GrimSpeed") && elytraDistance.isState());
    public final BooleanSetting predictMovement = new BooleanSetting("Предсказание движения", true).visible(() -> mode.is("GrimSpeed"));
    public final FloatSetting predictionFactor = new FloatSetting("Фактор предсказания", 2.0F, 1.0F, 5.0F, 0.1F).visible(() -> mode.is("GrimSpeed") && predictMovement.isState());
    public final BooleanSetting smoothMovement = new BooleanSetting("Плавное движение", true).visible(() -> mode.is("GrimSpeed"));
    public final BooleanSetting backtrack = new BooleanSetting("Backtrack", true).visible(() -> mode.is("GrimSpeed"));
    public final FloatSetting backtrackTicks = new FloatSetting("Тики отставания", 3.0F, 1.0F, 10.0F, 0.5F).visible(() -> mode.is("GrimSpeed") && backtrack.isState());
    public final FloatSetting backtrackDistance = new FloatSetting("Макс. дистанция BT", 6.0F, 3.0F, 15.0F, 0.5F).visible(() -> mode.is("GrimSpeed") && backtrack.isState());
    public final FloatSetting movementThreshold = new FloatSetting("Порог движения", 0.1F, 0.01F, 0.5F, 0.01F).visible(() -> mode.is("GrimSpeed"));
    public final FloatSetting staticBoostRange = new FloatSetting("Радиус буста", 0.45F, 0.1F, 1.0F, 0.05F).visible(() -> mode.is("GrimSpeed"));
    public final FloatSetting staticBoostForce = new FloatSetting("Сила буста", 0.085F, 0.01F, 0.2F, 0.005F).visible(() -> mode.is("GrimSpeed"));
    public final BooleanSetting disablePush = new BooleanSetting("Убирать push от игроков", true).visible(() -> mode.is("GrimSpeed"));

    private final double baseSpeedPerCollision = 0.085D;
    private final double maxSpeedPerTick = 0.38D;
    private final double maxTotalHSpeed = 0.485D;
    private final double searchRange = 3.2D;
    private final double expandBox = 0.45D;
    private final double skipChance = 0.25D;
    private final double sprintAngleRange = 45.0D;
    private final double walkAngleRange = 22.0D;
    private final double lerpStrength = 0.35D;
    private final double lerpFactor = 0.15D;
    private final int maxCollisions = 3;
    private final Random random = new Random();
    private final List<Vec3d> positionHistory = new ArrayList<>();
    private final List<Long> timeHistory = new ArrayList<>();
    private Entity target;
    private Vec3d lastTargetPos, predictedPos, backtrackPos, previousTargetPos;
    private double lastMotionX, lastMotionZ;
    private double targetMovementSpeed;
    private boolean targetStatic;

    public Speed() {
        super("Speed", "[Rage] Режимы ускорения движения (Collision / GrimSpeed)", ModuleCategory.MOVEMENT);
        addSettings(mode, onlyPlayers, requireMoving, pauseInLiquids, pauseWhileSneaking, speedFactor, distance,
                bypassDistance, bypassDistanceValue, bypassAngle, waterDistance, waterDistanceValue, elytraDistance, elytraDistanceValue,
                predictMovement, predictionFactor, smoothMovement, backtrack, backtrackTicks, backtrackDistance,
                movementThreshold, staticBoostRange, staticBoostForce, disablePush);
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;
        if (mode.is("Collision")) handleCollision();
        else updateTarget();
    }

    @EventLink
    public void onUpdatePost(EventUpdatePost event) {
        if (mc.player == null || mc.world == null || !mode.is("GrimSpeed")) return;
        handleGrimMotion();
    }

    private void handleCollision() {
        if (requireMoving.isState() && !isMoving()) return;
        if (random.nextDouble() < (mc.player.isSprinting() ? 0.8D : 0.3D)) return;

        int collisions = 0;
        Box checkBox = mc.player.getBoundingBox().expand(expandBox);
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (onlyPlayers.isState() && !(entity instanceof PlayerEntity)) continue;
            if (!(entity instanceof LivingEntity) && !(entity instanceof BoatEntity)) continue;
            if (checkBox.intersects(entity.getBoundingBox())) {
                collisions++;
                if (collisions >= 1) {
                    collisions = 1;
                    break;
                }
            }
        }
        if (collisions <= 0) return;

        Entity nearest = findNearestEntity(searchRange, null);
        if (nearest == null) return;
        Vec3d direction = nearest.getPos().subtract(mc.player.getPos()).normalize();
        double angle = (random.nextDouble() - 0.5D) * Math.toRadians(mc.player.isSprinting() ? 80.0D : 30.0D);
        Vec3d boost = rotateHorizontal(direction, angle).multiply(0.05D * (0.8D + random.nextDouble() * 0.4D));
        Vec3d velocity = mc.player.getVelocity().add(boost.x, 0.0D, boost.z);
        double horizontal = Math.hypot(velocity.x, velocity.z);
        double limit = mc.player.isSprinting() ? 0.35D : 0.4D;
        if (horizontal > limit) velocity = new Vec3d(velocity.x * limit / horizontal, velocity.y, velocity.z * limit / horizontal);
        mc.player.setVelocity(velocity);
    }

    private boolean shouldPauseGrimSpeed() {
        return (pauseInLiquids.isState() && mc.player.isInFluid()) || (pauseWhileSneaking.isState() && mc.player.isSneaking());
    }

    private void handleGrimMotion() {
        if (shouldPauseGrimSpeed()) return;
        if (!isMoving() || target == null || mc.player.hurtTime > 0) return;
        if (targetStatic) {
            applyStaticBoost();
            return;
        }
        Vec3d aim = backtrack.isState() && backtrackPos != null ? backtrackPos : predictedPos != null ? predictedPos : target.getPos();
        float effectiveDistance = getEffectiveDistance();
        if (mc.player.squaredDistanceTo(aim) > effectiveDistance * effectiveDistance) return;
        float slipperiness = mc.world.getBlockState(mc.player.getVelocityAffectingPos()).getBlock().getSlipperiness();
        double speed = speedFactor.get() * 0.01D * (mc.player.isOnGround() ? slipperiness * 0.91D : 0.91D);
        Vec3d desired = horizontalDirection(mc.player.getPos(), aim).multiply(speed);
        if (smoothMovement.isState()) desired = new Vec3d(lastMotionX + (desired.x - lastMotionX) * 0.6D, 0.0D, lastMotionZ + (desired.z - lastMotionZ) * 0.6D);
        lastMotionX = desired.x;
        lastMotionZ = desired.z;
        mc.player.addVelocity(desired.x, 0.0D, desired.z);
    }

    private void updateTarget() {
        if (shouldPauseGrimSpeed()) return;
        target = Aura.INSTANCE.getTarget();
        if (target == null) {
            clearState();
            return;
        }
        Vec3d current = target.getPos();
        if (previousTargetPos != null) {
            targetMovementSpeed = current.distanceTo(previousTargetPos);
            targetStatic = targetMovementSpeed < movementThreshold.get();
        }
        previousTargetPos = current;
        if (lastTargetPos == null) {
            lastTargetPos = current;
            predictedPos = current;
        } else {
            Vec3d velocity = current.subtract(lastTargetPos);
            predictedPos = predictMovement.isState() && !targetStatic ? current.add(velocity.multiply(predictionFactor.get())) : current;
            lastTargetPos = current;
        }
        if (backtrack.isState() && !targetStatic) updateBacktrack(current);
        else backtrackPos = null;
    }

    private void updateBacktrack(Vec3d current) {
        long now = System.currentTimeMillis();
        positionHistory.add(current);
        timeHistory.add(now);
        long maxAge = (long) (backtrackTicks.get() * 50.0F);
        while (!timeHistory.isEmpty() && now - timeHistory.get(0) > maxAge) {
            timeHistory.remove(0);
            positionHistory.remove(0);
        }
        backtrackPos = current;
        double best = mc.player.getPos().distanceTo(current);
        for (Vec3d position : positionHistory) {
            double distanceToPlayer = mc.player.getPos().distanceTo(position);
            if (current.distanceTo(position) <= backtrackDistance.get() && distanceToPlayer < best) {
                best = distanceToPlayer;
                backtrackPos = position;
            }
        }
    }

    private void applyStaticBoost() {
        if (!mc.player.getBoundingBox().expand(staticBoostRange.get()).intersects(target.getBoundingBox())) return;
        Vec3d direction = rotateHorizontal(target.getPos().subtract(mc.player.getPos()).normalize(), (random.nextDouble() - 0.5D) * 0.22D);
        mc.player.addVelocity(direction.x * staticBoostForce.get() * 0.88D, 0.0D, direction.z * staticBoostForce.get() * 0.88D);
    }

    private float getEffectiveDistance() {
        if (waterDistance.isState() && mc.player.isTouchingWater()) return waterDistanceValue.get();
        if (elytraDistance.isState() && mc.player.isGliding()) return elytraDistanceValue.get();
        if (!bypassDistance.isState() || target == null) return distance.get();
        Vec3d toPlayer = mc.player.getPos().subtract(target.getPos()).normalize();
        Vec3d targetLook = target.getRotationVec(1.0F);
        double dot = toPlayer.dotProduct(targetLook);
        return dot <= MathHelper.cos((float) Math.toRadians(bypassAngle.get())) ? bypassDistanceValue.get() : distance.get();
    }

    private Entity findNearestEntity(double range, Box collisionBox) {
        Entity nearest = null;
        double bestDistance = range * range;
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || (onlyPlayers.isState() && !(entity instanceof PlayerEntity)) || (!(entity instanceof LivingEntity) && !(entity instanceof BoatEntity))) continue;
            if (collisionBox != null && !collisionBox.intersects(entity.getBoundingBox())) continue;
            double distance = mc.player.squaredDistanceTo(entity);
            if (distance < bestDistance) { bestDistance = distance; nearest = entity; }
        }
        return nearest;
    }

    private boolean isMoving() { return mc.player.input.movementForward != 0.0F || mc.player.input.movementSideways != 0.0F; }
    private Vec3d horizontalDirection(Vec3d from, Vec3d to) {
        Vec3d delta = new Vec3d(to.x - from.x, 0.0D, to.z - from.z);
        return delta.lengthSquared() < 1.0E-6D ? Vec3d.ZERO : delta.normalize();
    }
    private Vec3d rotateHorizontal(Vec3d vector, double angle) {
        double sin = Math.sin(angle), cos = Math.cos(angle);
        return horizontalDirection(Vec3d.ZERO, new Vec3d(vector.x * cos - vector.z * sin, 0.0D, vector.x * sin + vector.z * cos));
    }
    private void clearState() {
        lastTargetPos = predictedPos = backtrackPos = previousTargetPos = null;
        targetStatic = false;
        targetMovementSpeed = 0.0D;
        lastMotionX = lastMotionZ = 0.0D;
        positionHistory.clear();
        timeHistory.clear();
    }
    @Override public void onEnable() { super.onEnable(); clearState(); }
    @Override public void onDisable() { super.onDisable(); clearState(); }
}
