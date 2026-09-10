package snill.client.client.modules.impl.movement;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.player.MoveUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.ModeSetting;
import ru.virtuoz.convert.Convert;

@Convert(Convert.ConvertType.MUTATION)
public class NoWeb extends Module {

    public static NoWeb INSTANCE = new NoWeb();
    private final ModeSetting mode = new ModeSetting("Режим", "CakeWorld", "CakeWorld", "ReallyWorld");

    public NoWeb() {
        super("NoWeb", "[Rage] Убирает замедление от паутины (Небезопасно для GrimAC)", ModuleCategory.MOVEMENT);
        addSettings(mode);
    }

    @EventLink
    @Convert(Convert.ConvertType.ULTRA)
    public void onUpdate(final EventUpdate eventUpdate) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (!mode.is("CakeWorld")) {
            return;
        }

        if (!mc.player.isSneaking() || !mc.player.isOnGround()) {
            boolean headInWeb = false;
            boolean feetInWeb = false;

            for (double x = -0.295; x <= 0.295; x += 0.05) {
                findHead:
                for (double z = -0.295; z <= 0.295; z += 0.05) {
                    for (double y = mc.player.getStandingEyeHeight(); y >= 0.0; y -= 0.1) {
                        BlockPos headPos = BlockPos.ofFloored(mc.player.getX() + x, mc.player.getY() + y, mc.player.getZ() + z);
                        if (mc.world.getBlockState(headPos).getBlock() != Blocks.COBWEB) {
                            continue;
                        }
                        headInWeb = true;
                        break findHead;
                    }
                }
            }

            if (!headInWeb) {
                findFeet:
                for (double x = -0.295; x <= 0.295; x += 0.05) {
                    for (double z = -0.295; z <= 0.295; z += 0.05) {
                        BlockPos pos = BlockPos.ofFloored(mc.player.getX() + x, mc.player.getY(), mc.player.getZ() + z);
                        if (mc.world.getBlockState(pos).getBlock() != Blocks.COBWEB) {
                            continue;
                        }
                        feetInWeb = true;
                        break findFeet;
                    }
                }
            }

            BlockPos aboveHeadPos = BlockPos.ofFloored(
                    mc.player.getX(),
                    mc.player.getY() + mc.player.getStandingEyeHeight() + 0.3f,
                    mc.player.getZ()
            );
            if (!headInWeb && !feetInWeb && mc.world.getBlockState(aboveHeadPos).getBlock() == Blocks.COBWEB) {
                headInWeb = true;
            }

            if (headInWeb || feetInWeb) {
                if (mc.options.jumpKey.isPressed()) {
                    mc.player.setVelocity(0.0, 1.4, 0.0);
                } else if (mc.options.sneakKey.isPressed()) {
                    mc.player.setVelocity(0.0, -3.5, 0.0);
                } else {
                    mc.player.setVelocity(0.0, 0.0, 0.0);
                }
                MoveUtils.setMotion(0.6);
            }
        }
    }
}
