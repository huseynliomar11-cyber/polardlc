package snill.client.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.api.utils.render.RenderUtils;
import snill.client.client.modules.impl.render.AspectRatio;
import snill.client.client.modules.impl.render.Removals;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    private float zoom;

    @Shadow
    private float zoomX;

    @Shadow
    private float zoomY;

    @Shadow
    public abstract float getFarPlaneDistance();

    @Shadow
    public abstract Camera getCamera();

    @Unique
    private boolean snill$renderingHand;

    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
    private void snill$hideTotemAnimation(ItemStack stack, CallbackInfo ci) {
        if (ModuleClass.INSTANCE == null || stack == null || !stack.isOf(Items.TOTEM_OF_UNDYING)) {
            return;
        }

        Removals removals = ModuleClass.removals;
        if (removals != null && removals.isTotemAnimationDisabled()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"
            )
    )
    private void snill$captureBlurBeforeHud(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        RenderUtils.beginLiquidBlurFrame();
    }

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void snill$onRenderHandHead(Camera camera, float tickDelta, Matrix4f matrix4f, CallbackInfo ci) {
        snill$renderingHand = true;
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void snill$onRenderHandReturn(Camera camera, float tickDelta, Matrix4f matrix4f, CallbackInfo ci) {
        snill$renderingHand = false;
    }

    @Inject(method = "getBasicProjectionMatrix", at = @At("HEAD"), cancellable = true)
    private void snill$applyAspectRatio(float fov, CallbackInfoReturnable<Matrix4f> cir) {
        if (ModuleClass.INSTANCE == null) {
            return;
        }

        AspectRatio aspectRatio = AspectRatio.INSTANCE;
        if (aspectRatio != null && aspectRatio.shouldApply()) {
            if (snill$renderingHand && !aspectRatio.getAffectHands().isState()) {
                return;
            }

            Matrix4f matrix4f = new Matrix4f();
            if (this.zoom != 1.0F) {
                matrix4f.translate(this.zoomX, -this.zoomY, 0.0F);
                matrix4f.scale(this.zoom, this.zoom, 1.0F);
            }

            float defaultAspect = aspectRatio.getDefaultRatio();
            float ratio = aspectRatio.getRatio(defaultAspect);

            cir.setReturnValue(matrix4f.perspective(
                    fov * 0.017453292F,
                    ratio,
                    0.05F,
                    this.getFarPlaneDistance()
            ));
        }
    }
}
