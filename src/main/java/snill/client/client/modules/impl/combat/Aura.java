package snill.client.client.modules.impl.combat;
import lombok.Getter;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import snill.client.Snill;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventAttackEntity;
import snill.client.api.events.implement.EventGameUpdate;
import snill.client.api.events.implement.EventMoveInput;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.events.implement.EventUpdatePost;
import snill.client.api.storages.implement.FreeLookStorage;
import snill.client.api.storages.implement.NeuroAuraStorage;
import snill.client.api.storages.implement.RotationStorage;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.api.utils.combat.IdealHitUtils;
import snill.client.api.utils.combat.RayTraceUtil;
import snill.client.api.utils.input.MovingUtil;
import snill.client.api.utils.math.TimerUtils;
import snill.client.api.utils.player.InventoryUtils;
import snill.client.api.utils.rotate.MultipointUtils;
import snill.client.api.utils.rotate.Rotation;
import snill.client.api.utils.rotate.RotationUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.impl.combat.components.RotationsSystem;
import snill.client.client.modules.impl.combat.components.interpolation.BestPoint;
import snill.client.client.modules.impl.combat.components.rotations.*;
import snill.client.client.modules.impl.movement.AirStuck;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ListSetting;
import snill.client.client.modules.settings.implement.ModeSetting;
import snill.client.mixin.ILivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static net.minecraft.util.math.MathHelper.wrapDegrees;

public class Aura extends Module {

    public static Aura INSTANCE = new Aura();

    public final ModeSetting rotationType = new ModeSetting("Ротация", "FunTime",
            "FunTime", "HolyWorld", "ReallyWorld", "SpookyTime", "SuperLegit", "HvH", "NoRotate");

    private final ListSetting targets = new ListSetting("Таргеты",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Невидимки", true),
            new BooleanSetting("Мирные", false),
            new BooleanSetting("Мобы", true),
            new BooleanSetting("Голые", true)
    );

    public final FloatSetting range = new FloatSetting("Дистанция атаки", 3f, 0f, 6f, 0.05f);
    private final FloatSetting aimRange = new FloatSetting("Дистанция наводки", 3f, 0f, 6f, 0.05f);
    private final FloatSetting elytraAimRange = new FloatSetting("Дистанция на элитрах", 50f, 10f, 100f, 0.05f);
    public final BooleanSetting smartCrit = new BooleanSetting("Умные криты", false);
    public final BooleanSetting sprintReset = new BooleanSetting("Сброс спринта", true);
    private final BooleanSetting throughWalls = new BooleanSetting("Бить через стены", false);
    private final BooleanSetting raycast = new BooleanSetting("Проверка на наведение", true);
    private final BooleanSetting unpressShield = new BooleanSetting("Отжимать щит", false);
    private final BooleanSetting breakShield = new BooleanSetting("Ломать щит", true);
    private final BooleanSetting attackOnEating = new BooleanSetting("Не бить когда ешь", true);
    public static BooleanSetting clientLook = new BooleanSetting("Наводка от первого лица", false);
    public final ModeSetting correctionType = new ModeSetting("Коррекция движения", "Свободная",
            "Свободная", "Сфокусированная", "Фулл Таргет", "Выключена");
    private final ModeSetting priority = new ModeSetting("Приоритет", "Дистанция", "Дистанция", "Здоровье", "Угол", "Никакой");

    @Getter
    private LivingEntity target;
    @Getter
    private Vec2f currentRotations = new Vec2f(0f, 0f);
    @Getter
    private Vec2f targetRotations = new Vec2f(0f, 0f);
    @Getter
    private final NeuroAuraStorage dataSystem = new NeuroAuraStorage();
    @Getter
    private final TimerUtils attackTimer = new TimerUtils();
    private final BooleanSetting rwWallBypass = new BooleanSetting("Обход рв стен", false);
    private final BooleanSetting syncTps = new BooleanSetting("Синхронизировать с ТПСом", false);
    private final FunTimeRotation funTimeRotation = new FunTimeRotation(this);
    private final HolyWorldRotation holyWorldRotation = new HolyWorldRotation(this);
    private final ReallyWorldRotation reallyWorldRotation = new ReallyWorldRotation(this);
    private final SpookyTimeRotation spookyTimeRotation = new SpookyTimeRotation();
    private final SuperLegitRotation superLegitRotation = new SuperLegitRotation(this);
    private final HvHRotation hvhRotation = new HvHRotation(this);
    private final TimerUtils backTimer = new TimerUtils();

    private long cps = 0;
    @Getter
    private boolean needSprintReset = false;
    private boolean sprintResetDone = false;
    private int sprintResetTicks = 0;
    private int ticksToAttack = 0;
    private int bypassAttackAge = -1;
    private boolean bypassAttackQueued = false;
    private boolean firstAttack = true;
    private LivingEntity lastDataTarget = null;
    public static float adjYaw;
    public static float adjPitch;
    public static float otvodkaYaw;
    public static float otvodkaPitch;

    public boolean isRotated;

    private Vec2f lastTargetRotation = null;
    private long targetLostTime = 0;
    private static final long ROTATION_HOLD_TIME = 0;

    public Aura() {
        super("AttackAura", "[FunTime / HolyWorld / ReallyWorld / Spooky] Автоматическая атака с обходом античитов", ModuleCategory.COMBAT);
        addSettings(rotationType, targets, range, aimRange, elytraAimRange, smartCrit, sprintReset, syncTps,
                attackOnEating, throughWalls, rwWallBypass, raycast, unpressShield, breakShield, clientLook, correctionType, priority);
    }
    @EventLink
    public void onPlayerTick(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;
        if (dataSystem.isRecording() && !rotationType.is("NoRotate")) {
            rotationType.set("NoRotate");
        }
        dataSystem.recordTick(null, mc.player.getYaw(), mc.player.getPitch());
        updateTarget();
    }
    @EventLink
    public void onAttackEntity(EventAttackEntity event) {
        if (mc.player == null || mc.world == null) return;
        if (event.getPlayer() != mc.player) return;
        if (!(event.getTarget() instanceof LivingEntity living)) return;
        // The mixin fires for both manual clicks and Aura attacks. This is the
        // single recording point, so one real attack can create only one sample.
        dataSystem.recordAttack(living, mc.player.getYaw(), mc.player.getPitch());
        dataSystem.onPlaybackAttack();
        if (!isValidTarget(living)) return;
        target = living;
    }

    @EventLink
    public void onMoveInput(EventMoveInput event) {
        boolean rotateActive = RotationStorage.instance != null && RotationStorage.instance.isRotating();
        if (target != null && rotateActive) {
            if (correctionType.is("Свободная")) {
                MovingUtil.fixMovementFree(event);
            } else if (correctionType.is("Сфокусированная")) {
                MovingUtil.fixMovementFocus(event, mc.player.getYaw());
            } else if (correctionType.is("Фулл Таргет")) {
                moveToTarget(event, target);
            }
        }

        if (needSprintReset) {
            event.setForward(0);
            event.setStrafe(0);
            needSprintReset = false;
            sprintResetDone = true;
            sprintResetTicks = 0;
        }
    }

    private void moveToTarget(EventMoveInput event, LivingEntity target) {
        if (mc.player == null || target == null) return;
        float forward = event.getForward();
        float strafe = event.getStrafe();
        if (forward == 0.0F && strafe == 0.0F) return;

        Vec3d delta = target.getPos().subtract(mc.player.getPos());
        double targetAngle = MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D);
        float bestForward = 0.0F;
        float bestStrafe = 0.0F;
        float smallestDifference = Float.MAX_VALUE;

        for (float testForward = -1.0F; testForward <= 1.0F; testForward++) {
            for (float testStrafe = -1.0F; testStrafe <= 1.0F; testStrafe++) {
                if (testForward == 0.0F && testStrafe == 0.0F) continue;
                double testAngle = MathHelper.wrapDegrees(Math.toDegrees(MovingUtil.direction(mc.player.getYaw(), testForward, testStrafe)));
                float difference = Math.abs(MathHelper.wrapDegrees((float) (targetAngle - testAngle)));
                if (difference < smallestDifference) {
                    smallestDifference = difference;
                    bestForward = testForward;
                    bestStrafe = testStrafe;
                }
            }
        }

        event.setForward(bestForward);
        event.setStrafe(bestStrafe);
    }

    @EventLink
    private void onGameUpdate(EventGameUpdate e) {
        if (mc.player == null || mc.world == null) {
            target = null;
            return;
        }
        if (target == null) return;
        rotate();
    }
    @EventLink
    public void onTick(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;
        if (ticksToAttack > 0) ticksToAttack--;
        processSpookyTimeSecondStrike();
        if (sprintResetDone) sprintResetTicks++;
        boolean packetCrits = ModuleClass.packetCriticals.isEnable() && mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING);
        if (!packetCrits) processAttack();
        if (dataSystem.isShowStats() && mc.player.age % 40 == 0 && (dataSystem.isRecording() || dataSystem.isUsingNeuro())) {
            mc.player.sendMessage(net.minecraft.text.Text.literal(dataSystem.getStatusString()), true);
        }
    }

    @EventLink
    public void onPost(EventUpdatePost e) {
        if (mc.player == null || mc.world == null) return;
        boolean packetCrits = ModuleClass.INSTANCE.packetCriticals.isEnable() && mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING);
        if (packetCrits && mc.player.fallDistance > 0 && mc.player.fallDistance < 1) processAttack();
    }
    private LivingEntity findTargetForRecording() {
        LivingEntity bestTarget = null;
        double bestDistance = 100.0;
        Vec3d eyePos = mc.player.getEyePos();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player) continue;
            if (!living.isAlive() || living.getHealth() <= 0) continue;
            if (living instanceof ArmorStandEntity) continue;
            double distance = eyePos.squaredDistanceTo(living.getBoundingBox().getCenter());
            if (distance > bestDistance) continue;
            bestDistance = distance;
            bestTarget = living;
        }
        return bestTarget;
    }
    private void processAttack() {
        updateTarget();
        if (target != null) {
            lastTargetRotation = new Vec2f(mc.player.getYaw(), mc.player.getPitch());
            targetLostTime = 0;
            if (shouldAttack() && cps <= System.currentTimeMillis()) {
                if (attackOnEating.isState() && mc.player.isUsingItem()) return;
                if (sprintReset.isState() && mc.player.isSprinting() && !sprintResetDone) {
                    needSprintReset = true;
                    return;
                }
                if (sprintReset.isState() && sprintResetDone && sprintResetTicks < 1) return;
                if (isBypassRotationActive() && !prepareBypassAttack()) return;
                attack();
                resetBypassAttack();
                sprintResetDone = false;
                sprintResetTicks = 0;
            }
        } else {
            funTimeRotation.reset();
            holyWorldRotation.reset();
            reallyWorldRotation.reset();
            spookyTimeRotation.reset();
            superLegitRotation.reset();
            hvhRotation.reset();
            if (lastTargetRotation != null && targetLostTime == 0) {
                targetLostTime = System.currentTimeMillis();
            }
            if (lastTargetRotation != null && System.currentTimeMillis() - targetLostTime < ROTATION_HOLD_TIME) {
                RotationStorage.update(new Rotation(lastTargetRotation.x, lastTargetRotation.y), 360, 360, 360, 360, 1, 1, clientLook.isState());
                return;
            }
            lastTargetRotation = null;
            targetLostTime = 0;
            cps = System.currentTimeMillis();
            backTimer.reset();
            adjPitch = 0;
            adjYaw = 0;
            firstAttack = true;
            dataSystem.resetState();
            lastDataTarget = null;
            sprintResetDone = false;
            sprintResetTicks = 0;
            ticksToAttack = 0;
            resetBypassAttack();
        }
    }

    private void processSpookyTimeSecondStrike() {
        if (!rotationType.is("SpookyTime") || !spookyTimeRotation.consumeSecondStrike()) return;
        if (target == null || !isValidTarget(target)) return;
        if (!shouldAttack()) return;

        attack();
    }

    public void Rotate() { rotate(); }
    private void rotate() {
        if (mc.player == null || mc.world == null || target == null) return;
        if (isNeuroRotation() && target != lastDataTarget) {
            dataSystem.resetState();
            lastDataTarget = target;
        }
        if (isBypassRotationActive()) {
            updateBypassRotation(target);
            return;
        }
        RotationsSystem system;
        if (rotationType.is("FunTime")) {
            system = funTimeRotation;
        } else if (rotationType.is("HolyWorld")) {
            system = holyWorldRotation;
        } else if (rotationType.is("ReallyWorld")) {
            system = reallyWorldRotation;
        } else if (rotationType.is("SpookyTime")) {
            system = spookyTimeRotation;
        } else if (rotationType.is("SuperLegit")) {
            system = superLegitRotation;
        } else if (rotationType.is("HvH")) {
            system = hvhRotation;
        } else if (rotationType.is("NoRotate")) {
            system = new RotationsSystem() {
                @Override
                public void updateRotations(LivingEntity target) {
                    if (RotationStorage.instance != null) RotationStorage.instance.stopRotation();
                    targetRotations = new Vec2f(mc.player.getYaw(), mc.player.getPitch());
                    currentRotations = targetRotations;
                }
            };
        } else if (isNeuroRotation()) {
            system = new RotationsSystem() {
                @Override
                public void updateRotations(LivingEntity target) {
                    boolean focusRotation = shouldFocusDataRotation();
                    Rotation rotation = dataSystem.getNeuroRotation(target, mc.player.getYaw(), mc.player.getPitch(), focusRotation);
                    if (rotation == null) {
                        Vec3d point = MultipointUtils.getClosestPoint(target);
                        Vec2f rot = RotationUtils.getRotations(getPredictedPoint(target, point != null ? point : target.getBoundingBox().getCenter()));
                        rotation = new Rotation(rot.x, rot.y);
                    }
                    targetRotations = new Vec2f(rotation.getYaw(), rotation.getPitch());
                    currentRotations = new Vec2f(mc.player.getYaw(), mc.player.getPitch());
                    float yawSpeed = dataSystem.isUsingNeuro() ? Math.max(dataSystem.getPlaybackYawSpeed(), focusRotation ? 40.0f : 18.0f) : (focusRotation ? 24.0f : 11.5f);
                    float pitchSpeed = dataSystem.isUsingNeuro() ? Math.max(dataSystem.getPlaybackPitchSpeed(), focusRotation ? 30.0f : 14.0f) : (focusRotation ? 18.0f : 9.0f);
                    RotationStorage.update(rotation, yawSpeed, pitchSpeed, yawSpeed, pitchSpeed, 1, 1, clientLook.isState());
                }
            };
        } else {
            system = funTimeRotation;
        }
        system.updateRotations(target);
    }
    private void updateBypassRotation(LivingEntity target) {
        Vec3d point = MultipointUtils.getClosestPoint(target);
        if (point == null) point = target.getBoundingBox().getCenter();
        Vec3d predicted = getPredictedRotationPoint(target, point);
        Vec2f targetRot = RotationUtils.getRotations(predicted);
        targetRotations = targetRot;
        currentRotations = new Vec2f(mc.player.getYaw(), mc.player.getPitch());
        boolean isBypassAttackTick = mc.player.age <= bypassAttackAge;
        float finalYaw = targetRot.x;
        float finalPitch = targetRot.y;
        if (!isBypassAttackTick) {
            finalYaw = FreeLookStorage.getFreeYaw();
            finalPitch = FreeLookStorage.getFreePitch();
        }
        RotationStorage.update(new Rotation(finalYaw, finalPitch), 360f, 360f, 360f, 360f, 0, 6, clientLook.isState());
    }

    private boolean isBypassRotationActive() { return isUsingRwWallBypass(); }

    private boolean prepareBypassAttack() {
        if (!bypassAttackQueued) {
            bypassAttackQueued = true;
            bypassAttackAge = mc.player.age + 1;
            return false;
        }
        if (mc.player.age > bypassAttackAge) {
            resetBypassAttack();
            return false;
        }
        return isBypassAimReadyForAttack();
    }
    private boolean isBypassAimReadyForAttack() {
        if (target == null || mc.player == null) return false;
        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetRotations.x - mc.player.getYaw()));
        float pitchDiff = Math.abs(targetRotations.y - mc.player.getPitch());
        boolean onTarget = isUsingRwWallBypass() || isCurrentAimOnTarget();
        return yawDiff <= 3.0f && pitchDiff <= 2.5f && onTarget;
    }

    private boolean isUsingRwWallBypass() { return rwWallBypass.isState() && target != null && isTargetBehindWall(target); }

    private EntityHitResult getAttackRaycastResult() {
        Vec3d eyePos = mc.player.getCameraPosVec(1.0F);
        Vec3d lookVec = mc.player.getRotationVec(1.0F);
        float reach = getEffectiveRange() * 2.0f;
        Vec3d reachVec = eyePos.add(lookVec.multiply(reach));
        return ProjectileUtil.raycast(mc.player, eyePos, reachVec, mc.player.getBoundingBox().expand(reach), ex -> ex != mc.player && ex.isAlive(), reach * reach);
    }

    private boolean isTargetBehindWall(LivingEntity entity) {
        return entity != null && mc.player != null && !mc.player.canSee(entity);
    }
    private Vec3d getPredictedRotationPoint(LivingEntity target, Vec3d point) {
        ElytraTarget elytraTarget = ElytraTarget.INSTANCE;
        if (mc.player != null && target != null && elytraTarget != null && elytraTarget.isPredictionActive()) {
            return elytraTarget.getPredictedPoint(target, point);
        }
        return point;
    }

    private Vec3d getPredictedPoint(LivingEntity target, Vec3d point) {
        ElytraTarget elytraTarget = ElytraTarget.INSTANCE;
        if (mc.player != null && target != null && elytraTarget != null && elytraTarget.isPredictionActive()) {
            return elytraTarget.getPredictedPoint(target, point);
        }
        return point;
    }

    private boolean isCurrentAimOnTarget() {
        if (target == null || mc.player == null) return false;
        if (mc.player.isGliding() && target.isGliding()) return RayTraceUtil.rayTraceEntity(mc.player.getYaw(), mc.player.getPitch(), getMaxAimRange(), target, false);
        EntityHitResult result = getAttackRaycastResult();
        return result != null && result.getEntity() == target;
    }
    private LivingEntity findTarget() {
        List<LivingEntity> entities = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isValidTarget(living)) continue;
            entities.add(living);
        }
        if (entities.isEmpty() || !isEnable()) return null;
        switch (priority.getCurrent()) {
            case "Дистанция" -> entities.sort(Comparator.comparingDouble(entity -> entity.getBoundingBox().getCenter().squaredDistanceTo(mc.player.getEyePos())));
            case "Здоровье" -> entities.sort(Comparator.comparingDouble(LivingEntity::getHealth));
            case "Угол" -> entities.sort(Comparator.comparingDouble(entity -> {
                Vec2f vec = RotationUtils.getRotations(entity.getBoundingBox().getCenter());
                double dy = Math.abs(wrapDegrees(vec.x - mc.player.getYaw()));
                double dp = Math.abs(wrapDegrees(vec.y - mc.player.getPitch()));
                return dy + dp;
            }));
        }
        return entities.isEmpty() ? null : entities.get(0);
    }
    private void updateTarget() {
        if (!isEnable()) { target = null; return; }
        if (target != null && isValidTarget(target)) return;
        target = findTarget();
    }
    private boolean shouldFocusDataRotation() {
        float cooldown = mc.player.getAttackCooldownProgress(1.5f);
        float focusThreshold = Math.max(0.82f, IdealHitUtils.getAICooldown() - 0.08f);
        boolean readyByCooldown = cooldown >= focusThreshold;
        boolean fallingForCrit = !mc.player.isOnGround() && mc.player.getVelocity().y < 0.0 && mc.player.fallDistance > 0.0f;
        return readyByCooldown || fallingForCrit;
    }
    private void attack() {
        if (unpressShield.isState() && mc.player.isBlocking()) mc.interactionManager.stopUsingItem(mc.player);
        tryBreakRwWallBlockPacket();
        boolean attacked = false;
        if (target instanceof PlayerEntity player && player.isBlocking() && breakShield.isState()) attacked = shieldBreak(player);
        if (!attacked) mc.interactionManager.attackEntity(mc.player, target);
        if (rotationType.is("FunTime")) funTimeRotation.onAttack();
        if (rotationType.is("HolyWorld")) holyWorldRotation.onAttack();
        if (rotationType.is("ReallyWorld")) reallyWorldRotation.onAttack();
        if (rotationType.is("SpookyTime")) spookyTimeRotation.onAttack();
        if (rotationType.is("SuperLegit")) superLegitRotation.onAttack();
        if (rotationType.is("HvH")) hvhRotation.onAttack();
        mc.player.swingHand(Hand.MAIN_HAND);
        long cooldown = 467L;
        if (syncTps.isState()) cooldown = (long) (getTpsAdjustedCooldown(cooldown) * 1.1f);
        cps = System.currentTimeMillis() + cooldown;
        ticksToAttack = 10;
        attackTimer.reset();
        firstAttack = false;
    }

    private float getSyncTpsValue() {
        if (Snill.INSTANCE == null || Snill.INSTANCE.tpsCalc == null) return 20.0f;
        float tps = Snill.INSTANCE.tpsCalc.getTPS();
        return MathHelper.clamp(tps, 0.1f, 20.0f);
    }
    private long getTpsAdjustedCooldown(long baseCooldown) {
        if (!syncTps.isState()) return baseCooldown;
        float tps = getSyncTpsValue();
        if (tps >= 20.0f) return baseCooldown;
        float multiplier = 20.0f / tps;
        float additionalFactor = 1.0f + (20.0f - tps) * 0.05f;
        long adjusted = (long) (baseCooldown * multiplier * additionalFactor);
        return Math.min(adjusted, 3000);
    }
    private void tryBreakRwWallBlockPacket() {
        if (!rwWallBypass.isState() || target == null || mc.player == null || mc.world == null) return;
        if (mc.player.canSee(target)) return;
        if (mc.player.networkHandler == null) return;
        Vec3d startVec = mc.player.getEyePos();
        Vec3d targetPos = getPredictedRotationPoint(target, target.getEyePos());
        Vec3d direction = targetPos.subtract(startVec);
        double distance = direction.length();
        if (distance < 1.0E-3D) return;
        Vec3d normalizedDir = direction.normalize();
        for (double i = 0.0D; i < distance; i += 0.5D) {
            Vec3d point = startVec.add(normalizedDir.multiply(i));
            BlockPos pos = BlockPos.ofFloored(point);
            if (mc.world.getBlockState(pos).isAir() || mc.world.getBlockState(pos).getHardness(mc.world, pos) < 0.0F) continue;
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
        }
    }

    private boolean shieldBreak(PlayerEntity entity) {
        if (mc.player == null || mc.interactionManager == null || entity == null) return false;
        int axeHotbarSlot = findAxeHotbarSlot();
        if (axeHotbarSlot != -1) { attackWithHotbarSlot(entity, axeHotbarSlot); return true; }
        int axeInventorySlot = findAxeInventorySlot();
        if (axeInventorySlot == -1) return false;
        int selectedSlot = mc.player.getInventory().selectedSlot;
        int containerSlot = InventoryUtils.toContainerSlot(axeInventorySlot);
        mc.interactionManager.clickSlot(0, containerSlot, selectedSlot, SlotActionType.SWAP, mc.player);
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
        try {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(selectedSlot));
            mc.interactionManager.attackEntity(mc.player, entity);
            return true;
        } finally {
            mc.interactionManager.clickSlot(0, containerSlot, selectedSlot, SlotActionType.SWAP, mc.player);
            mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(selectedSlot));
        }
    }

    private void attackWithHotbarSlot(PlayerEntity entity, int slot) {
        int previousSlot = mc.player.getInventory().selectedSlot;
        if (slot != previousSlot) { mc.player.getInventory().selectedSlot = slot; mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot)); }
        try { mc.interactionManager.attackEntity(mc.player, entity); } finally {
            if (slot != previousSlot) { mc.player.getInventory().selectedSlot = previousSlot; mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot)); }
        }
    }

    private int findAxeHotbarSlot() {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) return i;
        return -1;
    }

    private int findAxeInventorySlot() {
        for (int i = 9; i < 36; i++) if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) return i;
        return -1;
    }

    private boolean isWeapon() {
        Item item = mc.player.getMainHandStack().getItem();
        return item != Items.AIR && (item instanceof SwordItem || item instanceof PickaxeItem || item instanceof AxeItem || item instanceof HoeItem || item instanceof ShovelItem || item instanceof MaceItem || item == Items.MACE);
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (mc.player == null || mc.world == null || entity == null || entity == mc.player || !entity.isAlive() || entity.getHealth() <= 0 || entity instanceof ArmorStandEntity) return false;
        if (AntiBot.checkBot(entity)) return false;
        if (entity instanceof PlayerEntity player) {
            if (!targets.is("Игроки")) return false;
            if (!targets.is("Голые") && player.getArmor() == 0) return false;
            if (player.hasStatusEffect(StatusEffects.INVISIBILITY) && !targets.is("Невидимки")) return false;
            if (Snill.INSTANCE.friendStorage.isFriend(entity.getName().getString())) return false;
        } else if (entity instanceof HostileEntity) {
            if (!targets.is("Мобы")) return false;
        } else if (!targets.is("Мирные")) return false;
        Vec3d nearestPoint = BestPoint.getNearestPoint(entity);
        if (nearestPoint == null) nearestPoint = MultipointUtils.getClosestPoint(entity);
        if (mc.player.getEyePos().distanceTo(nearestPoint) > getMaxAimRange()) return false;
        return throughWalls.isState() || rwWallBypass.isState() || mc.player.canSee(entity);
    }

    private boolean shouldAttack() {
        if (mc.player.getAttackCooldownProgress(1.5f) < IdealHitUtils.getAICooldown()) return false;
        EntityHitResult result = getAttackRaycastResult();
        boolean aimOnTarget = isCurrentAimOnTarget();
        if (raycast.isState() && !isUsingRwWallBypass() && !aimOnTarget) return false;
        if (!throughWalls.isState() && !isUsingRwWallBypass() && !mc.player.canSee(target)) return false;
        if (isNeuroRotation() && !isUsingRwWallBypass() && !isDataAimReady(result, aimOnTarget)) return false;
        if (mc.player.isGliding() && target.isGliding()) {
            ElytraTarget elytraTarget = ElytraTarget.INSTANCE;
            // Prediction is for steering the flight path, not for delaying an
            // attack. Checking the projected point made the hit happen after
            // the pass; the current hitbox lets the crit fire on entry.
            Box targetBox = target.getBoundingBox();
            Vec3d eyePos = mc.player.getEyePos();
            Vec3d hitPoint = new Vec3d(
                    MathHelper.clamp(eyePos.x, targetBox.minX, targetBox.maxX),
                    MathHelper.clamp(eyePos.y, targetBox.minY, targetBox.maxY),
                    MathHelper.clamp(eyePos.z, targetBox.minZ, targetBox.maxZ)
            );
            double currentDistance = mc.player.getEyePos().distanceTo(hitPoint);
            // Do not packet-hit from inside the target's hitbox. At that range
            // flight desync makes the server flag the attack; the next approach
            // tick still lands the crit before we pass the target.
            return currentDistance >= 1.15D
                    && currentDistance <= getEffectiveRange()
                    && (elytraTarget == null || !elytraTarget.isPredictionActive() || elytraTarget.canCriticalDuringChase());
        } else {
            double distanceCheck = mc.player.getEyePos().distanceTo(target.getBoundingBox().getCenter());
            Vec3d checkPoint = distanceCheck > 3 ? BestPoint.getNearestPoint(target) : target.getBoundingBox().getCenter();
            if (checkPoint == null) checkPoint = MultipointUtils.getClosestPoint(target);
            if (mc.player.getEyePos().distanceTo(checkPoint) > getEffectiveRange()) return false;
            return IdealHitUtils.canCritical(target);
        }
    }

    private boolean isDataAimReady(EntityHitResult result, boolean aimOnTarget) {
        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetRotations.x - mc.player.getYaw()));
        float pitchDiff = Math.abs(targetRotations.y - mc.player.getPitch());
        boolean onTarget = (mc.player.isGliding() && target != null && target.isGliding()) ? aimOnTarget : result != null && result.getEntity() == target;
        return yawDiff <= 1.15f && pitchDiff <= 0.9f && onTarget;
    }

    private boolean isNeuroRotation() {
        return rotationType.is("Neuro");
    }

    public boolean isAboveWater() {
        BlockPos pos = BlockPos.ofFloored(mc.player.getPos().add(0, -0.4, 0));
        return !mc.player.isSubmergedInWater() && mc.world.getBlockState(pos).isOf(Blocks.WATER);
    }

    public float getAttackCooldown() { return MathHelper.clamp(((float) ((ILivingEntity) mc.player).getLastAttackedTicks()) / getAttackCooldownProgressPerTick(), 0.0F, 1.0F); }

    public float getAttackCooldownProgressPerTick() { return (float) (1.0 / mc.player.getAttributeValue(EntityAttributes.ATTACK_SPEED) * 20); }

    private float getMaxAimRange() { return mc.player.isGliding() ? elytraAimRange.getValue().floatValue() : getEffectiveRange() + aimRange.getValue().floatValue(); }

    private float getEffectiveRange() {
        float base = range.getValue().floatValue();
        if (AirStuck.INSTANCE.isEnable() && AirStuck.INSTANCE.extraRangeEnabled.isState()) base += AirStuck.INSTANCE.extraRange.getValue().floatValue();
        return base;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        funTimeRotation.reset();
        holyWorldRotation.reset();
        reallyWorldRotation.reset();
        spookyTimeRotation.reset();
        superLegitRotation.reset();
        hvhRotation.reset();
        if (target != null) backTimer.reset();
        target = null;
        dataSystem.resetState();
        lastDataTarget = null;
        needSprintReset = false;
        sprintResetDone = false;
        sprintResetTicks = 0;
        ticksToAttack = 0;
        resetBypassAttack();
        firstAttack = true;
        lastTargetRotation = null;
        targetLostTime = 0;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        funTimeRotation.reset();
        holyWorldRotation.reset();
        reallyWorldRotation.reset();
        spookyTimeRotation.reset();
        superLegitRotation.reset();
        hvhRotation.reset();
        dataSystem.resetState();
        lastDataTarget = null;
        needSprintReset = false;
        sprintResetDone = false;
        sprintResetTicks = 0;
        ticksToAttack = 0;
        resetBypassAttack();
        firstAttack = true;
        lastTargetRotation = null;
        targetLostTime = 0;
        if (mc.player != null) currentRotations = new Vec2f(mc.player.getYaw(), mc.player.getPitch());
    }

    private void resetBypassAttack() { bypassAttackAge = -1; bypassAttackQueued = false; }

    public boolean isFirstAttack() { return firstAttack; }

    public float getNormalizedAttackCooldown() { return getAttackCooldown(); }

    public int getWhiteRiseTicksToAttack() { return ticksToAttack; }
}
