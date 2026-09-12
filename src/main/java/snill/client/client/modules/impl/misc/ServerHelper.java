package snill.client.client.modules.impl.misc;

import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventBinding;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.chat.ChatUtils;
import snill.client.api.utils.player.InventoryUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BindSetting;
import snill.client.client.modules.settings.implement.ModeSetting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ServerHelper extends Module {

    public static ServerHelper INSTANCE = new ServerHelper();

    private final ModeSetting mode = new ModeSetting("Режим", "FunTime", "FunTime", "HolyWorld", "ReallyWorld", "Spooky");

    private final BindSetting trapkaFT = new BindSetting("Трапка", -1).visible(() -> mode.is("FunTime"));
    private final BindSetting stunFT = new BindSetting("Стан", -1).visible(() -> mode.is("FunTime"));
    private final BindSetting snowFT = new BindSetting("Снег", -1).visible(() -> mode.is("FunTime"));
    private final BindSetting dustFT = new BindSetting("Пыль", -1).visible(() -> mode.is("FunTime"));
    private final BindSetting sphereFT = new BindSetting("Сфера / Шар", -1).visible(() -> mode.is("FunTime"));

    private final BindSetting stickHW = new BindSetting("Взрыв штучка", -1).visible(() -> mode.is("HolyWorld"));
    private final BindSetting gulHW = new BindSetting("Гул", -1).visible(() -> mode.is("HolyWorld"));
    private final BindSetting stunHW = new BindSetting("Стан", -1).visible(() -> mode.is("HolyWorld"));
    private final BindSetting trapkaHW = new BindSetting("Взрыв трап", -1).visible(() -> mode.is("HolyWorld"));
    private final BindSetting snowHW = new BindSetting("Снег", -1).visible(() -> mode.is("HolyWorld"));
    private final BindSetting trapkHW = new BindSetting("Трапка", -1).visible(() -> mode.is("HolyWorld"));

    private final BindSetting antipoletRW = new BindSetting("Анти Полет", -1).visible(() -> mode.is("ReallyWorld"));
    private final BindSetting lovushkaRW = new BindSetting("Ловушка", -1).visible(() -> mode.is("ReallyWorld"));

    private final BindSetting disorientationSP = new BindSetting("Дезориентация", -1).visible(() -> mode.is("Spooky"));
    private final BindSetting trapSP = new BindSetting("Трапка", -1).visible(() -> mode.is("Spooky"));
    private final BindSetting plastSP = new BindSetting("Пласт", -1).visible(() -> mode.is("Spooky"));
    private final BindSetting pilSP = new BindSetting("Явная пыль", -1).visible(() -> mode.is("Spooky"));
    private final BindSetting snegSP = new BindSetting("Снег заморозки", -1).visible(() -> mode.is("Spooky"));
    private final BindSetting auraSP = new BindSetting("Божья аура", -1).visible(() -> mode.is("Spooky"));

    private Item pendingItem;
    private Action pendingAction;

    public ServerHelper() {
        super("ServerHelper", "[FunTime / HolyWorld / ReallyWorld / Spooky] Помощник для серверов", ModuleCategory.MISC);
        addSettings(
                mode,
                trapkaFT, stunFT, snowFT, dustFT, sphereFT,
                stickHW, gulHW, stunHW, trapkaHW, snowHW, trapkHW,
                antipoletRW, lovushkaRW,
                disorientationSP, trapSP, plastSP, pilSP, snegSP, auraSP
        );
    }

    public boolean isFunTimeMode() {
        return mode.is("FunTime");
    }

    public boolean isSpookyMode() {
        return mode.is("Spooky");
    }

    public boolean isHolyWorldMode() {
        return mode.is("HolyWorld");
    }

    public boolean isReallyWorldMode() {
        return mode.is("ReallyWorld");
    }

    public List<HelperBind> getActiveHelperBinds() {
        if (mode.is("FunTime")) return getFunTimeHelperBinds();
        if (mode.is("HolyWorld")) return getHolyWorldHelperBinds();
        if (mode.is("ReallyWorld")) return getReallyWorldHelperBinds();
        if (mode.is("Spooky")) return getSpookyHelperBinds();
        return List.of();
    }

    public List<HelperBind> getAllHelperBinds() {
        List<HelperBind> binds = new ArrayList<>();
        binds.addAll(getFunTimeHelperBinds());
        binds.addAll(getHolyWorldHelperBinds());
        binds.addAll(getReallyWorldHelperBinds());
        binds.addAll(getSpookyHelperBinds());
        return binds;
    }

    public List<HelperBind> getFunTimeHelperBinds() {
        return List.of(
                new HelperBind("Трапка", Items.POPPED_CHORUS_FRUIT, trapkaFT),
                new HelperBind("Стан", Items.NETHER_STAR, stunFT),
                new HelperBind("Снег", Items.SNOWBALL, snowFT),
                new HelperBind("Пыль", Items.SUGAR, dustFT),
                new HelperBind("Сфера / Шар", Items.PLAYER_HEAD, sphereFT)
        );
    }

    public String resolveHelperBindName(Item item) {
        String currentModeName = resolveHelperBindName(getActiveHelperBinds(), item);
        if (currentModeName != null) {
            return currentModeName;
        }

        return resolveHelperBindName(getAllHelperBinds(), item);
    }

    private String resolveHelperBindName(List<HelperBind> binds, Item item) {
        String resolved = null;
        for (HelperBind helperBind : binds) {
            if (helperBind.item() != item) continue;
            if (resolved == null) {
                resolved = helperBind.name();
                continue;
            }
            if (!resolved.equals(helperBind.name())) {
                return null;
            }
        }
        return resolved;
    }

    public List<HelperBind> getHolyWorldHelperBinds() {
        return List.of(
                new HelperBind("Взрыв штучка", Items.FIRE_CHARGE, stickHW),
                new HelperBind("Гул", Items.FIREWORK_STAR, gulHW),
                new HelperBind("Стан", Items.NETHER_STAR, stunHW),
                new HelperBind("Взрыв трап", Items.PRISMARINE_SHARD, trapkaHW),
                new HelperBind("Снег", Items.SNOWBALL, snowHW),
                new HelperBind("Трапка", Items.POPPED_CHORUS_FRUIT, trapkHW)
        );
    }

    public List<HelperBind> getReallyWorldHelperBinds() {
        return List.of(
                new HelperBind("Анти Полет", Items.FIREWORK_STAR, antipoletRW),
                new HelperBind("Ловушка", Items.HEART_OF_THE_SEA, lovushkaRW)
        );
    }

    public List<HelperBind> getSpookyHelperBinds() {
        return List.of(
                new HelperBind("Дезориентация", Items.ENDER_EYE, disorientationSP),
                new HelperBind("Трапка", Items.NETHERITE_SCRAP, trapSP),
                new HelperBind("Пласт", Items.DRIED_KELP, plastSP),
                new HelperBind("Явная пыль", Items.SUGAR, pilSP),
                new HelperBind("Снег заморозки", Items.SNOWBALL, snegSP),
                new HelperBind("Божья аура", Items.PHANTOM_MEMBRANE, auraSP)
        );
    }

    public record HelperBind(String name, Item item, BindSetting bind) {
    }

    @Override
    public void onEnable() {
        pendingItem = null;
        pendingAction = null;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        pendingItem = null;
        pendingAction = null;
        super.onDisable();
    }

    @EventLink
    public void onBinding(EventBinding event) {
        if (mc.currentScreen != null) return;
        int key = event.getKey();

        if (mode.is("FunTime")) {
            if (key == trapkaFT.getKey()) pendingAction = Action.TRAP_FT;
            else if (key == stunFT.getKey()) pendingAction = Action.STUN_FT;
            else if (key == snowFT.getKey()) pendingAction = Action.SNOW_FT;
            else if (key == dustFT.getKey()) pendingAction = Action.DUST_FT;
            else if (key == sphereFT.getKey()) pendingAction = Action.SPHERE_FT;
            return;
        }

        if (mode.is("HolyWorld")) {
            if (key == stickHW.getKey()) pendingAction = Action.STICK_HW;
            else if (key == gulHW.getKey()) pendingAction = Action.GUL_HW;
            else if (key == stunHW.getKey()) pendingAction = Action.STUN_HW;
            else if (key == trapkaHW.getKey()) pendingAction = Action.TRAPKA_HW;
            else if (key == snowHW.getKey()) pendingAction = Action.SNOW_HW;
            else if (key == trapkHW.getKey()) pendingAction = Action.TRAPK_HW;
            return;
        }

        if (mode.is("ReallyWorld")) {
            if (key == antipoletRW.getKey()) pendingAction = Action.ANTIPOLET_RW;
            else if (key == lovushkaRW.getKey()) pendingAction = Action.LOVUSHKA_RW;
            return;
        }

        if (mode.is("Spooky")) {
            if (key == disorientationSP.getKey()) pendingAction = Action.DISORIENTATION_SP;
            else if (key == trapSP.getKey()) pendingAction = Action.TRAP_SP;
            else if (key == plastSP.getKey()) pendingAction = Action.PLAST_SP;
            else if (key == pilSP.getKey()) pendingAction = Action.DUST_SP;
            else if (key == snegSP.getKey()) pendingAction = Action.FREEZE_SNOW_SP;
            else if (key == auraSP.getKey()) pendingAction = Action.AURA_SP;
        }
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) {
            pendingItem = null;
            pendingAction = null;
            return;
        }

        if (pendingAction == null || mc.interactionManager == null) return;

        if (mode.is("ReallyWorld")) {
            if (pendingAction == Action.ANTIPOLET_RW) {
                if (!InventoryUtils.hasItem(Items.FIREWORK_STAR)) {
                    ChatUtils.sendMessage("Анти полет не найден!");
                    pendingAction = null;
                    return;
                }

                if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.FIREWORK_STAR))) {
                    ChatUtils.sendMessage("У предмета полёта есть кд");
                    pendingAction = null;
                    return;
                }

                boolean used = InventoryUtils.antipoletrwfix(Items.FIREWORK_STAR);
                ChatUtils.sendMessage(used ? "Использовал анти полет!" : "Анти полет не найден!");
                pendingAction = null;
                return;
            }

            if (pendingAction == Action.LOVUSHKA_RW) {
                useSimpleItem(Items.HEART_OF_THE_SEA, "Использовал ловушку!", "Ловушка не найдена!");
                pendingAction = null;
                return;
            }
        }

        useAction(pendingAction);
        pendingAction = null;
    }

    private void useSimpleItem(Item item, String successText, String failText) {
        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(item))) {
            ChatUtils.sendMessage("У предмета есть кд");
            return;
        }

        if (mc.player.getMainHandStack().getItem() == item) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            ChatUtils.sendMessage(successText);
            return;
        }

        if (mc.player.getOffHandStack().getItem() == item) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            mc.player.swingHand(Hand.OFF_HAND);
            ChatUtils.sendMessage(successText);
            return;
        }

        int hotbarSlot = findItemSlot(item, 0, 8);
        if (hotbarSlot != -1) {
            int previousSlot = mc.player.getInventory().selectedSlot;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(hotbarSlot));
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            ChatUtils.sendMessage(successText);
            return;
        }

        int inventorySlot = findItemSlot(item, 9, 35);
        if (inventorySlot != -1) {
            useFromInventorySlot(inventorySlot);
            ChatUtils.sendMessage(successText);
            return;
        }

        ChatUtils.sendMessage(failText);
    }

    private void useAction(Action action) {
        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(action.item))) {
            ChatUtils.sendMessage("У предмета " + action.cooldownName + " есть кд");
            return;
        }

        if (matchesAction(mc.player.getMainHandStack(), action)) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            ChatUtils.sendMessage(action.successText);
            return;
        }

        if (matchesAction(mc.player.getOffHandStack(), action)) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            mc.player.swingHand(Hand.OFF_HAND);
            ChatUtils.sendMessage(action.successText);
            return;
        }

        int hotbarSlot = findMatchingSlot(action, 0, 8);
        if (hotbarSlot != -1) {
            int previousSlot = mc.player.getInventory().selectedSlot;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(hotbarSlot));
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            ChatUtils.sendMessage(action.successText);
            return;
        }

        int inventorySlot = findMatchingSlot(action, 9, 35);
        if (inventorySlot != -1) {
            useFromInventorySlot(inventorySlot);
            ChatUtils.sendMessage(action.successText);
            return;
        }

        ChatUtils.sendMessage(action.failText);
    }

    private void useFromInventorySlot(int inventorySlot) {
        int previousSlot = mc.player.getInventory().selectedSlot;
        int hotbarSlot = findTemporaryHotbarSlot();

        int screenSlot = toScreenSlot(inventorySlot);

        mc.interactionManager.clickSlot(0, screenSlot, hotbarSlot, SlotActionType.SWAP, mc.player);
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(hotbarSlot));
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
        mc.interactionManager.clickSlot(0, screenSlot, hotbarSlot, SlotActionType.SWAP, mc.player);
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
    }

    private int findTemporaryHotbarSlot() {
        int fallback = 8;
        for (int slot = 0; slot < 9; slot++) {
            if (slot == mc.player.getInventory().selectedSlot) continue;
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) return slot;
            if (stack.getUseAction() == UseAction.NONE) fallback = slot;
        }
        return fallback;
    }

    private int findItemSlot(Item item, int start, int end) {
        for (int slot = start; slot <= end; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return slot;
            }
        }
        return -1;
    }

    private int findMatchingSlot(Action action, int start, int end) {
        for (int slot = start; slot <= end; slot++) {
            if (matchesAction(mc.player.getInventory().getStack(slot), action)) return slot;
        }
        return -1;
    }

    private int toScreenSlot(int inventorySlot) {
        if (inventorySlot >= 0 && inventorySlot <= 8) {
            return 36 + inventorySlot;
        }
        return inventorySlot;
    }

    private boolean matchesAction(ItemStack stack, Action action) {
        if (stack == null || stack.isEmpty() || stack.getItem() != action.item) return false;
        return stack.getName().getString().toLowerCase(Locale.ROOT).contains(action.query);
    }

    private enum Action {
        TRAP_FT("трапк", "трапки", Items.POPPED_CHORUS_FRUIT, "Использовал трапку!", "Трапка не найдена!"),
        STUN_FT("стан", "стана", Items.NETHER_STAR, "Использовал стан!", "Стан не найден!"),
        SNOW_FT("снег", "снега", Items.SNOWBALL, "Использовал снег!", "Снег не найден!"),
        DUST_FT("пыль", "пыли", Items.SUGAR, "Использовал пыль!", "Пыль не найдена!"),
        SPHERE_FT("шар", "шара", Items.PLAYER_HEAD, "Использовал шар!", "Шар не найден!"),

        STICK_HW("взрыв", "штучки", Items.FIRE_CHARGE, "Использовал взрыв штучку!", "Штучка не найдена!"),
        GUL_HW("гул", "гула", Items.FIREWORK_STAR, "Использовал гул!", "Гул не найден!"),
        STUN_HW("стан", "стана", Items.NETHER_STAR, "Использовал стан!", "Стан не найден!"),
        TRAPKA_HW("взрыв", "трапки", Items.PRISMARINE_SHARD, "Использовал взрыв трап!", "Взрыв трап не найден!"),
        SNOW_HW("снег", "снега", Items.SNOWBALL, "Использовал снег!", "Снег не найден!"),
        TRAPK_HW("трапка", "трапки", Items.POPPED_CHORUS_FRUIT, "Использовал трапку!", "Трапка не найдена!"),

        ANTIPOLET_RW("анти", "полёта", Items.FIREWORK_STAR, "Использовал анти полет!", "Анти полет не найден!"),
        LOVUSHKA_RW("ловушка", "ловушки", Items.HEART_OF_THE_SEA, "Использовал ловушку!", "Ловушка не найдена!"),

        DISORIENTATION_SP("дезориентация", "дезориентации", Items.ENDER_EYE, "Использовал дезориентацию!", "Дезориентация не найдена!"),
        TRAP_SP("трапка", "трапки", Items.NETHERITE_SCRAP, "Использовал трапку!", "Трапка не найдена!"),
        PLAST_SP("пласт", "пласта", Items.DRIED_KELP, "Использовал пласт!", "Пласт не найден!"),
        DUST_SP("явная пыль", "пыли", Items.SUGAR, "Использовал пыль!", "Пыль не найдена!"),
        FREEZE_SNOW_SP("заморозка", "снега", Items.SNOWBALL, "Использовал снег!", "Снег не найден!"),
        AURA_SP("божья", "ауры", Items.PHANTOM_MEMBRANE, "Использовал ауру!", "Аура не найдена!");

        private final String query;
        private final String cooldownName;
        private final Item item;
        private final String successText;
        private final String failText;

        Action(String query, String cooldownName, Item item, String successText, String failText) {
            this.query = query;
            this.cooldownName = cooldownName;
            this.item = item;
            this.successText = successText;
            this.failText = failText;
        }
    }
}
