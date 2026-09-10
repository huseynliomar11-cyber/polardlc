package snill.client.client.modules.impl.player;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventMoveInput;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.input.MovingUtil;
import snill.client.api.utils.player.InventoryUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

public class AutoArmor extends Module {

    public static AutoArmor INSTANCE = new AutoArmor();

    private final ModeSetting server = new ModeSetting("Сервер", "FunTime", "FunTime", "HolyWorld", "ReallyWorld", "Универсальный");
    private final FloatSetting delay = new FloatSetting("Задержка (мс)", 70f, 30f, 300f, 10f)
            .visible(() -> server.is("Универсальный"));
    private final BooleanSetting blastProtection = new BooleanSetting("Приоритет Взрывозащиты", false);
    private final BooleanSetting bypassGrim = new BooleanSetting("Обход Grim", true);
    private final BooleanSetting openInventoryOnly = new BooleanSetting("Только в инвентаре", false);

    private long lastEquipTime = 0L;
    private int bypassTicks = 0;
    private int pendingArmorSlot = -1;
    private int pendingFromSlot = -1;
    private boolean pendingHasArmor = false;

    // Armor container slot IDs in PlayerScreenHandler:
    // 5 = Helmet, 6 = Chestplate, 7 = Leggings, 8 = Boots
    private static final int[] ARMOR_CONTAINER_SLOTS = new int[]{5, 6, 7, 8};
    private static final EquipmentSlot[] EQUIPMENT_SLOTS = new EquipmentSlot[]{
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    public AutoArmor() {
        super("AutoArmor", "[Все серверы / FunTime / HolyWorld] Автоматически надевает лучшую броню", ModuleCategory.PLAYER);
        addSettings(server, blastProtection, bypassGrim, openInventoryOnly, delay);
    }

    private long getEffectiveDelay() {
        if (server.is("FunTime")) return 85L;
        if (server.is("HolyWorld")) return 70L;
        if (server.is("ReallyWorld")) return 120L;
        return (long) delay.get();
    }

    @EventLink
    public void onMoveInput(final EventMoveInput e) {
        if (bypassGrim.isState() && bypassTicks > 0) {
            if (mc.player == null) return;
            mc.player.setSprinting(false);
            e.setForward(0);
            e.setStrafe(0);
            e.setJump(false);
            e.setSneak(false);
        }
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // Don't equip if in a chest, anvil, or other custom GUI
        if (mc.currentScreen != null && !(mc.currentScreen instanceof HandledScreen<?> handled && handled.getScreenHandler() instanceof PlayerScreenHandler)) {
            return;
        }

        if (bypassGrim.isState() && bypassTicks > 0) {
            mc.player.setSprinting(false);
            bypassTicks--;
            if (bypassTicks <= 0 && pendingFromSlot != -1) {
                equipArmor(pendingArmorSlot, pendingFromSlot, pendingHasArmor);
                pendingArmorSlot = -1;
                pendingFromSlot = -1;
                lastEquipTime = System.currentTimeMillis();
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastEquipTime < getEffectiveDelay()) return;

        // Check each armor slot (Head, Chest, Legs, Feet)
        for (int type = 0; type < 4; type++) {
            EquipmentSlot eqSlot = EQUIPMENT_SLOTS[type];
            int armorContainerSlot = ARMOR_CONTAINER_SLOTS[type];

            ItemStack currentArmor = mc.player.getEquippedStack(eqSlot);
            float currentScore = getArmorScore(currentArmor);

            int bestSlot = -1;
            float bestScore = currentScore;

            // Search inventory slots 9..44 in PlayerScreenHandler
            // 9..35 is main inventory, 36..44 is hotbar
            for (int slot = 9; slot <= 44; slot++) {
                ItemStack stack = mc.player.playerScreenHandler.getSlot(slot).getStack();
                if (stack.isEmpty()) continue;

                EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
                if (equippable == null || equippable.slot() != eqSlot) continue;

                float score = getArmorScore(stack);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = slot;
                }
            }

            if (bestSlot != -1) {
                if (openInventoryOnly.isState() && mc.currentScreen == null) return;
                boolean moving = MovingUtil.hasPlayerMovement() || mc.player.isSprinting();
                if (bypassGrim.isState() && mc.currentScreen == null && moving) {
                    pendingArmorSlot = armorContainerSlot;
                    pendingFromSlot = bestSlot;
                    pendingHasArmor = !currentArmor.isEmpty();
                    bypassTicks = 2;
                    mc.player.setSprinting(false);
                    return;
                }
                equipArmor(armorContainerSlot, bestSlot, !currentArmor.isEmpty());
                lastEquipTime = System.currentTimeMillis();
                return; // One action per delay for GrimAC safety
            }
        }
    }

    @Override
    public void onDisable() {
        bypassTicks = 0;
        pendingArmorSlot = -1;
        pendingFromSlot = -1;
        super.onDisable();
    }

    private void equipArmor(int armorContainerSlot, int fromSlot, boolean hasCurrentArmor) {
        if (mc.interactionManager == null || mc.player == null) return;

        int syncId = mc.player.playerScreenHandler.syncId;

        if (!hasCurrentArmor) {
            // Empty armor slot -> quick move (shift-click) directly into armor slot
            mc.interactionManager.clickSlot(syncId, fromSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
        } else {
            // Swap: pick up new armor, place in armor slot (picks up old armor), place old armor in fromSlot
            mc.interactionManager.clickSlot(syncId, fromSlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, armorContainerSlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, fromSlot, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private float getArmorScore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return -1f;
        }

        Item item = stack.getItem();
        float baseScore = getBaseDefense(item);
        if (baseScore <= 0f) {
            return -1f;
        }

        float score = baseScore * 3.0f;

        // Enchantment scores using 1.21.4 registry keys
        int prot = InventoryUtils.getEnchantmentLevel(stack, Enchantments.PROTECTION);
        int blast = InventoryUtils.getEnchantmentLevel(stack, Enchantments.BLAST_PROTECTION);
        int fire = InventoryUtils.getEnchantmentLevel(stack, Enchantments.FIRE_PROTECTION);
        int proj = InventoryUtils.getEnchantmentLevel(stack, Enchantments.PROJECTILE_PROTECTION);
        int unbreaking = InventoryUtils.getEnchantmentLevel(stack, Enchantments.UNBREAKING);
        int mending = InventoryUtils.getEnchantmentLevel(stack, Enchantments.MENDING);

        if (blastProtection.isState()) {
            score += blast * 3.5f;
            score += prot * 2.0f;
        } else {
            score += prot * 3.0f;
            score += blast * 2.2f;
        }

        score += fire * 0.8f;
        score += proj * 0.8f;
        score += unbreaking * 0.5f;
        score += mending * 0.5f;

        // Reduce score if durability is critically low (< 10%)
        if (stack.isDamageable()) {
            float maxDamage = stack.getMaxDamage();
            float currentDamage = stack.getDamage();
            float healthRatio = 1.0f - (currentDamage / maxDamage);
            if (healthRatio < 0.1f) {
                score -= 10f;
            }
        }

        return score;
    }

    private float getBaseDefense(Item item) {
        // Helmets
        if (item == Items.NETHERITE_HELMET) return 6.0f;
        if (item == Items.DIAMOND_HELMET) return 5.0f;
        if (item == Items.TURTLE_HELMET) return 3.0f;
        if (item == Items.IRON_HELMET) return 3.0f;
        if (item == Items.GOLDEN_HELMET) return 2.0f;
        if (item == Items.CHAINMAIL_HELMET) return 2.0f;
        if (item == Items.LEATHER_HELMET) return 1.0f;

        // Chestplates
        if (item == Items.NETHERITE_CHESTPLATE) return 11.0f;
        if (item == Items.DIAMOND_CHESTPLATE) return 10.0f;
        if (item == Items.IRON_CHESTPLATE) return 6.0f;
        if (item == Items.GOLDEN_CHESTPLATE) return 5.0f;
        if (item == Items.CHAINMAIL_CHESTPLATE) return 5.0f;
        if (item == Items.LEATHER_CHESTPLATE) return 3.0f;

        // Leggings
        if (item == Items.NETHERITE_LEGGINGS) return 9.0f;
        if (item == Items.DIAMOND_LEGGINGS) return 8.0f;
        if (item == Items.IRON_LEGGINGS) return 5.0f;
        if (item == Items.GOLDEN_LEGGINGS) return 3.0f;
        if (item == Items.CHAINMAIL_LEGGINGS) return 4.0f;
        if (item == Items.LEATHER_LEGGINGS) return 2.0f;

        // Boots
        if (item == Items.NETHERITE_BOOTS) return 6.0f;
        if (item == Items.DIAMOND_BOOTS) return 5.0f;
        if (item == Items.IRON_BOOTS) return 3.0f;
        if (item == Items.GOLDEN_BOOTS) return 2.0f;
        if (item == Items.CHAINMAIL_BOOTS) return 2.0f;
        if (item == Items.LEATHER_BOOTS) return 1.0f;

        return 0.0f;
    }
}
