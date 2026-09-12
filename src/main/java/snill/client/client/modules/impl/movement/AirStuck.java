package snill.client.client.modules.impl.movement;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Vec3d;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventMove;
import snill.client.api.events.implement.EventPacket;
import snill.client.api.utils.player.InventoryUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;

public class AirStuck extends Module {
    public static AirStuck INSTANCE = new AirStuck();

    private final BooleanSetting timingBypass = new BooleanSetting("Стопить в тайминг", false);

    public final BooleanSetting extraRangeEnabled = new BooleanSetting("Доп дистанция вкл", false);
    public final FloatSetting extraRange = new FloatSetting("Доп дистанция", 1.0f, 1.0f, 5.0f, 0.1f);

    private Vec3d freezePosition = Vec3d.ZERO;
    private boolean frozen = false;
    private boolean swapped = false;

    public AirStuck() {
        super("Air Stuck", "[Rage] Зависание в воздухе и доп. дистанция (Небезопасно для GrimAC)", ModuleCategory.MOVEMENT);
        addSettings(timingBypass, extraRangeEnabled, extraRange);
    }
    @Override
    public void onEnable() {
        frozen = false;
        swapped = false;

        if (mc.player != null) {
            if (!timingBypass.isState()) {
                freezePosition = mc.player.getPos();
                frozen = true;
            }

            {
                ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
                if (chestStack.isOf(Items.ELYTRA)) {
                    int slot = InventoryUtils.findBestChestplateSlot();
                    if (slot != -1) {
                        performSwap(slot);
                        swapped = true;
                    }
                }
            }
        }

        super.onEnable();
    }

    @Override
    public void onDisable() {
        {
            int slot = InventoryUtils.findBestElytraSlot();
            if (slot != -1) {
                performSwap(slot);
            }
            swapped = false;
        }
        frozen = false;
        super.onDisable();
    }

    private void performSwap(int slot) {
        if (slot >= 0 && slot < 9) {
            mc.interactionManager.clickSlot(0, 6, slot, SlotActionType.SWAP, mc.player);
        } else {
            mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, 6, 0, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
        }
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
    }

    @EventLink
    public void onMove(final EventMove e) {
        if (mc.player == null) return;

        if (timingBypass.isState() && !frozen) {
            if (mc.player.fallDistance > 0.0F && mc.player.getVelocity().y < 0.0D) {
                freezePosition = mc.player.getPos();
                frozen = true;
            }
        }

        if (frozen) {
            e.setMovePos(Vec3d.ZERO);
            mc.player.setPosition(freezePosition.x, freezePosition.y, freezePosition.z);
            mc.player.setVelocity(0, 0, 0);
        }
    }

    @EventLink
    public void onPacket(final EventPacket e) {
        if (!frozen || mc.player == null) return;

        if (e.getPacket() instanceof PlayerMoveC2SPacket) {
            e.cancel();
        }
    }

    public float getExtraRangeValue() {
        if (isEnable() && extraRangeEnabled.isState()) {
            return extraRange.getValue().floatValue();
        }
        return 0f;
    }
}