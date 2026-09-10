package snill.client.api.storages.implement.helpertstorages.enumvar;


import snill.client.client.modules.Module;
import ru.virtuoz.convert.Convert;

import java.util.List;

public class ModuleClass extends GlobalObject<Module> implements ModuleRewords {

    public static ModuleClass INSTANCE = new ModuleClass();
    @Convert(Convert.ConvertType.ULTRA)
    public void initialize() {
        this.add(
                interfaceModule,
                antibot,
                antithorns,
                aimBot,
                airStuck,
                arrows,
                aura,
                autoAccept,
                ahHelper,
                searchHelper,
                autoDuel,
                autoLeave,
                autoExplosion,
                nameProtect,
                autoSwap,
                autoTool,
                autoTotem,
                blockesp,
                blockOverlay,
                chams,
                clientSounds,
                clickPearl,
                test,
                cosmetics,
                cubes,
                elytraBoost,
                elytraMotion,
                elytraSwap,
                elytraTarget,
                entityESP,
                fireworkESP,
                fastExp,
                freeCam,
                fullBright,
                grimGlide,
                hitBubbles,
                particles,
                dashTrails,
                hitMarker,
                itemReplacer,
                interpolateF5,
                inventoryWalk,
                itemRelease,
                itemScroller,
                jumpCircle,
                trails,
                killEffect,
                leavetracker,
                lootTracker,
                noJumpDelay,
                noPush,
                noControllerWeb,
                noWeb,
                pets,
                packetCriticals,
                projectile,
                potionTracker,
                scoreboardHP,
                removals,
                seeInvisibles,
                shaderEsp,
                shaderHands,
                serverHelper,
                shulkerPreview,
                sonar,
                smoothSwapping,
                speed,
                sprint,
                swingAnimations,
                targetESP,
                hitEffect,
                totemAngel,
                totemEffect,
                tracers,
                trajectories,
                viewModel,
                worldTweaks,
                velocity,
                noSlow,
                targetStrafe,
                maceHelper,
                autoBuff,
                autoEat,
                chestStealer,
                anchorAura,
                autoWeb,
                autoArmor,
                fastBreak,
                browser,
                hitSounds,
                customCrosshair,
                aspectRatio
        );
    }

    private void add(final Module... mod) {
        this.getObject().addAll(List.of(mod));
    }
}
