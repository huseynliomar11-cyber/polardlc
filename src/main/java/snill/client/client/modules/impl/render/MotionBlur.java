package snill.client.client.modules.impl.render;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

@Getter
public class MotionBlur extends Module {

    public static MotionBlur INSTANCE = new MotionBlur();

    private final ModeSetting mode = new ModeSetting(
            "Режим",
            "TikTok Эдит",
            "TikTok Эдит",
            "Динамический PvP",
            "Lunar"
    );

    private final FloatSetting intensity = new FloatSetting("Интенсивность", 7.5f, 1.0f, 10.0f, 0.5f);

    private final BooleanSetting smoothCamera = new BooleanSetting("Плавная камера", true);

    private SimpleFramebuffer historyFbo;

    private float lastYaw;
    private float lastPitch;
    private boolean initializedAngles;
    private float currentAlpha = 0.0f;

    private double smoothMouseX;
    private double smoothMouseY;

    public MotionBlur() {
        super("MotionBlur", "Кинематографичное размытие и плавность камеры", ModuleCategory.RENDER);
        addSettings(mode, intensity, smoothCamera);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        initializedAngles = false;
        currentAlpha = 0.0f;
        smoothMouseX = 0.0;
        smoothMouseY = 0.0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        deleteHistoryBuffer();
        initializedAngles = false;
        currentAlpha = 0.0f;
        smoothMouseX = 0.0;
        smoothMouseY = 0.0;
    }

    public boolean shouldSmoothCamera() {
        return isEnable() && smoothCamera.isState();
    }

    public double smoothMouse(double target, boolean isX) {
        if (!isEnable() || !smoothCamera.isState()) {
            return target;
        }

        double factor = 0.55;

        if (isX) {
            if (Math.abs(target) < 0.0001) {
                smoothMouseX *= 0.15;
                if (Math.abs(smoothMouseX) < 0.01) smoothMouseX = 0.0;
                return smoothMouseX;
            }
            smoothMouseX = smoothMouseX + (target - smoothMouseX) * factor;
            return smoothMouseX;
        } else {
            if (Math.abs(target) < 0.0001) {
                smoothMouseY *= 0.15;
                if (Math.abs(smoothMouseY) < 0.01) smoothMouseY = 0.0;
                return smoothMouseY;
            }
            smoothMouseY = smoothMouseY + (target - smoothMouseY) * factor;
            return smoothMouseY;
        }
    }

    public void applyMotionBlur(Camera camera) {
        if (!isEnable()) {
            return;
        }

        if (mc.player == null || mc.world == null) {
            deleteHistoryBuffer();
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

        // Если открыт интерфейс (инвентарь, чат, меню) — не размываем UI, сохраняем экран чистым
        if (mc.currentScreen != null) {
            if (historyFbo != null) {
                copyFramebuffer(mainFbo, historyFbo, width, height);
            }
            return;
        }

        if (historyFbo == null || historyFbo.textureWidth != width || historyFbo.textureHeight != height) {
            deleteHistoryBuffer();
            historyFbo = createHistoryBuffer(width, height);
            copyFramebuffer(mainFbo, historyFbo, width, height);
            return;
        }

        float yaw = camera != null ? camera.getYaw() : mc.player.getYaw();
        float pitch = camera != null ? camera.getPitch() : mc.player.getPitch();

        if (!initializedAngles) {
            lastYaw = yaw;
            lastPitch = pitch;
            initializedAngles = true;
            copyFramebuffer(mainFbo, historyFbo, width, height);
            return;
        }

        float dy = Math.abs(yaw - lastYaw);
        float dp = Math.abs(pitch - lastPitch);
        if (dy > 180.0f) dy = Math.abs(dy - 360.0f);
        lastYaw = yaw;
        lastPitch = pitch;

        boolean isMoving = false;
        double speedSq = mc.player.getVelocity().horizontalLengthSquared();
        if (speedSq > 0.0005 || Math.abs(mc.player.getVelocity().y) > 0.005) {
            isMoving = true;
        }

        float motionDelta = dy + dp;
        float alpha = calculateBlendAlpha(motionDelta, isMoving);

        if (alpha <= 0.01f) {
            // Камера и игрок неподвижны в динамическом режиме — обновляем буфер на 100% четкий кадр
            copyFramebuffer(mainFbo, historyFbo, width, height);
            return;
        }

        float scaledW = mc.getWindow().getScaledWidth();
        float scaledH = mc.getWindow().getScaledHeight();

        RenderSystem.backupProjectionMatrix();
        try {
            Matrix4f ortho = new Matrix4f().setOrtho(0.0F, scaledW, scaledH, 0.0F, -1000.0F, 3000.0F);
            RenderSystem.setProjectionMatrix(ortho, ProjectionType.ORTHOGRAPHIC);

            mainFbo.beginWrite(true);

            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();

            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            RenderSystem.setShaderTexture(0, historyFbo.getColorAttachment());

            BufferBuilder buffer = Tessellator.getInstance().begin(
                    VertexFormat.DrawMode.QUADS,
                    VertexFormats.POSITION_TEXTURE_COLOR
            );
            buffer.vertex(0.0f, 0.0f, 0.0f).texture(0.0f, 1.0f).color(1.0f, 1.0f, 1.0f, alpha);
            buffer.vertex(0.0f, scaledH, 0.0f).texture(0.0f, 0.0f).color(1.0f, 1.0f, 1.0f, alpha);
            buffer.vertex(scaledW, scaledH, 0.0f).texture(1.0f, 0.0f).color(1.0f, 1.0f, 1.0f, alpha);
            buffer.vertex(scaledW, 0.0f, 0.0f).texture(1.0f, 1.0f).color(1.0f, 1.0f, 1.0f, alpha);
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

        // Сохраняем получившийся скомпонованный кадр в накопитель для следующего шага
        copyFramebuffer(mainFbo, historyFbo, width, height);

        // Возвращаем запись в основной буфер для последующего рендера HUD
        mainFbo.beginWrite(true);
    }

    private float calculateBlendAlpha(float motionDelta, boolean isMoving) {
        String currentMode = mode.getCurrent();
        float level = intensity.get();

        switch (currentMode) {
            case "TikTok Эдит" -> {
                // Плотный кинематографичный шлейф (0.50 -> 0.88)
                float baseAlpha = 0.45f + ((level - 1.0f) / 9.0f) * 0.43f;
                if (motionDelta > 0.05f || isMoving) {
                    currentAlpha = MathHelper.lerp(0.40f, currentAlpha, baseAlpha);
                } else {
                    // При остановке мягко снижаем до 55% от базового, чтобы не было застоя
                    currentAlpha = MathHelper.lerp(0.35f, currentAlpha, baseAlpha * 0.55f);
                }
                return currentAlpha;
            }
            case "Динамический PvP" -> {
                // В движении сочно и плавно, при остановке мыши моментально четкий прицел
                float baseAlpha = 0.35f + ((level - 1.0f) / 9.0f) * 0.45f;
                if (motionDelta > 0.06f || isMoving) {
                    currentAlpha = MathHelper.lerp(0.50f, currentAlpha, baseAlpha);
                } else {
                    // Быстрое затухание при остановке камеры
                    currentAlpha *= 0.30f;
                    if (currentAlpha < 0.04f) {
                        currentAlpha = 0.0f;
                    }
                }
                return currentAlpha;
            }
            case "Lunar" -> {
                // Классический Phosphor MotionBlur
                return 0.25f + ((level - 1.0f) / 9.0f) * 0.60f;
            }
            default -> {
                return 0.50f;
            }
        }
    }

    private SimpleFramebuffer createHistoryBuffer(int w, int h) {
        SimpleFramebuffer fb = new SimpleFramebuffer(w, h, false);
        fb.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        fb.clear();
        RenderSystem.bindTexture(fb.getColorAttachment());
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);
        RenderSystem.bindTexture(0);
        return fb;
    }

    private void copyFramebuffer(Framebuffer src, Framebuffer dst, int width, int height) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dst.fbo);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    private void deleteHistoryBuffer() {
        if (historyFbo != null) {
            historyFbo.delete();
            historyFbo = null;
        }
    }
}
