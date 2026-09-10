package snill.client.client.modules.impl.render;

import lombok.Getter;
import net.minecraft.util.math.MathHelper;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

@Getter
public class AspectRatio extends Module {

    public static AspectRatio INSTANCE = new AspectRatio();

    private final ModeSetting mode = new ModeSetting(
            "Режим",
            "4:3",
            "4:3",
            "16:9",
            "16:10",
            "5:4",
            "1:1",
            "21:9",
            "32:9",
            "Custom"
    );

    private final FloatSetting customRatio = new FloatSetting("Соотношение", 1.33f, 0.30f, 3.00f, 0.05f);
    private final BooleanSetting affectHands = new BooleanSetting("Влиять на руки", true);
    private final BooleanSetting smooth = new BooleanSetting("Плавность", true);

    private float animatedRatio = -1.0f;

    public AspectRatio() {
        super("AspectRatio", "Изменяет соотношение сторон и растягивает экран", ModuleCategory.RENDER);
        customRatio.visible(() -> mode.is("Custom"));
        addSettings(mode, customRatio, affectHands, smooth);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (animatedRatio <= 0.0f) {
            animatedRatio = getDefaultRatio();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (!smooth.isState()) {
            animatedRatio = getDefaultRatio();
        }
    }

    public boolean shouldApply() {
        if (isEnable()) {
            return true;
        }
        if (smooth.isState() && animatedRatio > 0.0f) {
            float defaultAspect = getDefaultRatio();
            return Math.abs(animatedRatio - defaultAspect) > 0.002f;
        }
        return false;
    }

    public float getRatio(float defaultAspect) {
        float target = isEnable() ? getTargetRatio(defaultAspect) : defaultAspect;
        if (!smooth.isState()) {
            animatedRatio = target;
            return target;
        }

        if (animatedRatio <= 0.0f) {
            animatedRatio = defaultAspect;
        }

        animatedRatio = MathHelper.lerp(0.18f, animatedRatio, target);

        if (!isEnable() && Math.abs(animatedRatio - defaultAspect) <= 0.002f) {
            animatedRatio = defaultAspect;
            return defaultAspect;
        }

        return animatedRatio;
    }

    public float getTargetRatio(float fallback) {
        return switch (mode.getCurrent()) {
            case "4:3" -> 4.0f / 3.0f;
            case "16:9" -> 16.0f / 9.0f;
            case "16:10" -> 16.0f / 10.0f;
            case "5:4" -> 5.0f / 4.0f;
            case "1:1" -> 1.0f;
            case "21:9" -> 21.0f / 9.0f;
            case "32:9" -> 32.0f / 9.0f;
            case "Custom" -> customRatio.get();
            default -> fallback;
        };
    }

    public float getDefaultRatio() {
        if (mc.getWindow() == null || mc.getWindow().getFramebufferHeight() <= 0) {
            return 16.0f / 9.0f;
        }
        return (float) mc.getWindow().getFramebufferWidth() / (float) mc.getWindow().getFramebufferHeight();
    }
}
