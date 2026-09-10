package snill.client.client.modules.impl.combat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.effect.StatusEffects;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventUpdate;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.ModeSetting;

public class AntiBot extends Module {

    public static AntiBot INSTANCE = new AntiBot();

    public static final List<Entity> isBot = new ArrayList<>();

    private static final String MODE_MATRIX = "Matrix";
    private static final String MODE_DEFAULT = "Default";
    private static final String MODE_CAKE_ARTY = "Cake/Arty";

    private final ModeSetting mode = new ModeSetting("Режим", MODE_MATRIX, MODE_MATRIX, MODE_DEFAULT, MODE_CAKE_ARTY);

    public AntiBot() {
        super("AntiBot", "Удаляет ботов от античита", ModuleCategory.COMBAT);
        addSettings(mode);
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        this.newMatrix();
    }

    public void newMatrix() {
        if (mc.world == null || mc.player == null) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (mc.player == player) {
                continue;
            }

            boolean shouldRemove = isBotCandidate(player);

            if (shouldRemove) {
                if (!isBot.contains(player)) {
                    isBot.add(player);
                }
            } else {
                isBot.remove(player);
            }
        }
    }

    private boolean isBotCandidate(PlayerEntity player) {
        if (player == null) return false;

        if (mode.is(MODE_CAKE_ARTY)) {
            return isCakeArtyBot(player);
        }

        // Core anticheat aura check: fake entities never exist in the tab list
        if (isNotInTabList(player)) {
            return true;
        }

        // Invisible bait entities spawned around the player
        if (isInvisibleTarget(player) && countArmorPieces(player) == 0) {
            double distSq = mc.player.squaredDistanceTo(player);
            if (distSq <= 25.0) {
                return true;
            }
        }

        return false;
    }

    private boolean isNotInTabList(PlayerEntity player) {
        if (mc.getNetworkHandler() == null) return false;
        return mc.getNetworkHandler().getPlayerListEntry(player.getUuid()) == null;
    }

    private boolean isCakeArtyBot(PlayerEntity player) {
        if (player == null) {
            return false;
        }

        if (!isInvisibleTarget(player)) {
            return false;
        }

        return countArmorPieces(player) == 0;
    }

    private boolean isInvisibleTarget(PlayerEntity player) {
        if (player.isInvisible()) {
            return true;
        }

        if (player.hasStatusEffect(StatusEffects.INVISIBILITY)) {
            return true;
        }

        return mc.player != null && player.isInvisibleTo(mc.player);
    }

    private int countArmorPieces(PlayerEntity player) {
        int armorCount = 0;
        for (ItemStack stack : player.getInventory().armor) {
            if (stack != null && !stack.isEmpty() && stack.getItem() != Items.AIR) {
                armorCount++;
            }
        }
        return armorCount;
    }

    public static boolean checkBot(LivingEntity entity) {
        if (!INSTANCE.isEnable()) return false;
        return entity instanceof PlayerEntity && isBot.contains(entity);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        isBot.clear();
    }
}
