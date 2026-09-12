package snill.client.client.modules.impl.combat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.effect.StatusEffects;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventUpdate;
import snill.client.client.modules.Module;

public class AntiBot extends Module {

    public static AntiBot INSTANCE = new AntiBot();

    public static final List<Entity> isBot = new ArrayList<>();

    public AntiBot() {
        super("AntiBot", "Универсальный антибот (Matrix / GrimAC / NPC)", ModuleCategory.COMBAT);
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        this.updateBots();
    }

    public void updateBots() {
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
        if (player == null || mc.player == null) return false;

        // 1. Главная проверка: фейковые энтити и боты античитов (Matrix/AAC/NPC) никогда не имеют записи в TabList
        PlayerListEntry tabEntry = getTabListEntry(player);
        if (tabEntry == null) {
            return true;
        }

        // 2. Проверка ботов-приманок в невидимости:
        // Настоящие игроки, выпившие зелье невидимости, есть в таб-листе и живут в мире долго.
        // Боты от античитов (Matrix aura bot) спавнятся прямо перед/над игроком на 1-2 секунды (age < 80)
        // без брони и с невалидным/нулевым пингом.
        if (isInvisibleTarget(player) && countArmorPieces(player) == 0) {
            if (player.age < 80 && mc.player.squaredDistanceTo(player) <= 25.0) {
                if (tabEntry.getLatency() <= 0) {
                    return true;
                }
            }
        }

        return false;
    }

    private PlayerListEntry getTabListEntry(PlayerEntity player) {
        if (mc.getNetworkHandler() == null) return null;
        return mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
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
