package snill.client.client.modules.impl.render;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.FloatSetting;

@Getter
public class MotionBlur extends Module {

    public static MotionBlur INSTANCE = new MotionBlur();

    private final FloatSetting intensity = new FloatSetting("Интенсивность", 5.0f, 1.0f, 10.0f, 0.5f);

    private Framebuffer accumulationFbo;

    public MotionBlur() {
        super("MotionBlur", "Плавное размытие движения камеры", ModuleCategory.RENDER);
        addSettings(intensity);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        deleteAccumulationBuffer();
    }

    private void deleteAccumulationBuffer() {
        if (accumulationFbo != null) {
            accumulationFbo.delete();
            accumulationFbo = null;
        }
    }

    public void applyMotionBlur() {
        if (mc.player == null || mc.world == null) {
            deleteAccumulationBuffer();
            return;
        }

        Framebuffer mainFbo = mc.getFramebuffer();
        if (mainFbo == null) {
            return;
        }

        int width = mainFbo.textureWidth;
        int height = mainFbo.textureHeight;
        if (width <= 0 || height <= 0) {
            return;
        }

        if (accumulationFbo == null || accumulationFbo.textureWidth != width || accumulationFbo.textureHeight != height) {
            deleteAccumulationBuffer();
            accumulationFbo = new SimpleFramebuffer(width, height, false);
            accumulationFbo.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            accumulationFbo.clear();

            // Копируем первый кадр без наложения
            copyFramebuffer(mainFbo, accumulationFbo, width, height);
            return;
        }

        // Вычисляем коэффициент накопления:
        // 1.0 -> 0.18f (легкий шлейф), 5.0 -> 0.50f (золотая середина), 10.0 -> 0.82f (глубокий blur)
        float level = intensity.get();
        float alpha = 0.18f + ((level - 1.0f) / 9.0f) * 0.64f;

        RenderSystem.backupProjectionMatrix();
        try {
            mainFbo.beginWrite(false);

            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();

            Matrix4f identity = new Matrix4f().identity();
            RenderSystem.setProjectionMatrix(identity, ProjectionType.ORTHOGRAPHIC);

            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            RenderSystem.setShaderTexture(0, accumulationFbo.getColorAttachment());

            BufferBuilder buffer = Tessellator.getInstance().begin(
                    VertexFormat.DrawMode.QUADS,
                    VertexFormats.POSITION_TEXTURE_COLOR
            );
            buffer.vertex(-1.0f, -1.0f, 0.0f).texture(0.0f, 0.0f).color(1.0f, 1.0f, 1.0f, alpha);
            buffer.vertex(-1.0f, 1.0f, 0.0f).texture(0.0f, 1.0f).color(1.0f, 1.0f, 1.0f, alpha);
            buffer.vertex(1.0f, 1.0f, 0.0f).texture(1.0f, 1.0f).color(1.0f, 1.0f, 1.0f, alpha);
            buffer.vertex(1.0f, -1.0f, 0.0f).texture(1.0f, 0.0f).color(1.0f, 1.0f, 1.0f, alpha);
            BufferRenderer.drawWithGlobalProgram(buffer.end());

            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        } finally {
            RenderSystem.restoreProjectionMatrix();
        }

        // Сохраняем скомпонованный кадр в накопительный буфер для следующего кадра
        copyFramebuffer(mainFbo, accumulationFbo, width, height);

        // Возвращаем запись в основной буфер
        mainFbo.beginWrite(false);
    }

    private void copyFramebuffer(Framebuffer src, Framebuffer dst, int width, int height) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dst.fbo);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }
}
