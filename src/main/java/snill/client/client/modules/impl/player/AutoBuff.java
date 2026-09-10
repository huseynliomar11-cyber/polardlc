package snill.client.client.modules.impl.player;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.storages.implement.RotationStorage;
import snill.client.api.utils.rotate.Rotation;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

public class AutoBuff extends Module {
    public static AutoBuff INSTANCE = new AutoBuff();

    public final ModeSetting mode = new ModeSetting("Режим", "Safe", "Safe", "Rage");
    public final BooleanSetting speed = new BooleanSetting("Скорость", true);
    public final BooleanSetting strength = new BooleanSetting("Сила", true);
    public final BooleanSetting fireRes = new BooleanSetting("Огнестойкость", true);
    public final BooleanSetting regeneration = new BooleanSetting("Регенерация", true);
    public final FloatSetting delay = new FloatSetting("Задержка (мс)", 500.0f, 200.0f, 1500.0f, 50.0f);
    public final BooleanSetting onlyGround = new BooleanSetting("Только на земле", true);

    private long lastThrowTime = 0;

    private enum SafeState { IDLE, AIM, RESTORE }
    private SafeState safeState = SafeState.IDLE;
    private int targetSlot = -1;
    private int previousSlot = -1;
    private int stateTicks = 0;

    public AutoBuff() {
        super("AutoBuff", "Автоматически кидает взрывные зелья баффов", ModuleCategory.PLAYER);
        addSettings(mode, speed, strength, fireRes, regeneration, delay, onlyGround);
    }

    @EventLink
    public void onUpdate(final EventUpdate event) {
        if (mc.player == null || mc.world == null) {
            resetSafeState();
            return;
        }

        if (safeState != SafeState.IDLE) {
            handleSafeState();
            return;
        }

        if (onlyGround.isState() && !mc.player.isOnGround()) return;
        if (mc.player.isUsingItem()) return;
        if (System.currentTimeMillis() - lastThrowTime < (long) delay.get()) return;

        if (speed.isState() && needsEffect(StatusEffects.SPEED)) {
            int slot = findPotionSlot(StatusEffects.SPEED);
            if (slot != -1) {
                triggerPotionThrow(slot);
                return;
            }
        }

        if (strength.isState() && needsEffect(StatusEffects.STRENGTH)) {
            int slot = findPotionSlot(StatusEffects.STRENGTH);
            if (slot != -1) {
                triggerPotionThrow(slot);
                return;
            }
        }

        if (fireRes.isState() && needsEffect(StatusEffects.FIRE_RESISTANCE)) {
            int slot = findPotionSlot(StatusEffects.FIRE_RESISTANCE);
            if (slot != -1) {
                triggerPotionThrow(slot);
                return;
            }
        }

        if (regeneration.isState() && needsEffect(StatusEffects.REGENERATION)) {
            int slot = findPotionSlot(StatusEffects.REGENERATION);
            if (slot != -1) {
                triggerPotionThrow(slot);
            }
        }
    }

    private void triggerPotionThrow(int slot) {
        if (mode.is("Safe")) {
            targetSlot = slot;
            previousSlot = mc.player.getInventory().selectedSlot;
            safeState = SafeState.AIM;
            stateTicks = 0;
        } else {
            throwPotionRage(slot);
        }
    }

    private void handleSafeState() {
        stateTicks++;
        switch (safeState) {
            case AIM -> {
                Rotation targetRotation = new Rotation(mc.player.getYaw(), 88.5f);
                RotationStorage.update(targetRotation, 180f, 180f, 180f, 180f, 1, 3, false);

                if (Math.abs(mc.player.getPitch() - 88.5f) < 8.0f || stateTicks >= 3) {
                    if (targetSlot != -1) {
                        if (targetSlot != mc.player.getInventory().selectedSlot) {
                            mc.player.getInventory().selectedSlot = targetSlot;
                            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(targetSlot));
                        }
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    }
                    safeState = SafeState.RESTORE;
                    stateTicks = 0;
                }
            }
            case RESTORE -> {
                if (previousSlot != -1 && mc.player.getInventory().selectedSlot != previousSlot) {
                    mc.player.getInventory().selectedSlot = previousSlot;
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
                }
                resetSafeState();
                lastThrowTime = System.currentTimeMillis();
            }
        }
    }

    private void resetSafeState() {
        safeState = SafeState.IDLE;
        targetSlot = -1;
        previousSlot = -1;
        stateTicks = 0;
    }

    @Override
    public void onDisable() {
        resetSafeState();
        super.onDisable();
    }

    private boolean needsEffect(RegistryEntry<StatusEffect> effect) {
        if (mc.player == null) return false;
        StatusEffectInstance instance = mc.player.getStatusEffect(effect);
        return instance == null || instance.getDuration() <= 20;
    }

    private int findPotionSlot(RegistryEntry<StatusEffect> targetEffect) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.SPLASH_POTION) {
                PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
                if (contents != null && hasEffect(contents, targetEffect)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean hasEffect(PotionContentsComponent contents, RegistryEntry<StatusEffect> targetEffect) {
        for (StatusEffectInstance instance : contents.getEffects()) {
            if (instance.getEffectType().equals(targetEffect)) {
                return true;
            }
        }
        return false;
    }

    private void throwPotionRage(int slot) {
        if (mc.player == null || mc.interactionManager == null || mc.player.networkHandler == null) return;
        int oldSlot = mc.player.getInventory().selectedSlot;

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                mc.player.getYaw(), 90.0f, mc.player.isOnGround(), false));

        if (slot != oldSlot) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            mc.player.getInventory().selectedSlot = slot;
        }

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);

        if (slot != oldSlot) {
            mc.player.getInventory().selectedSlot = oldSlot;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
        }

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround(), false));

        lastThrowTime = System.currentTimeMillis();
    }
}
