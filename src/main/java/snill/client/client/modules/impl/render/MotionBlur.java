package snill.client.client.modules.impl.render;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import snill.client.api.utils.render.ShaderUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

@Getter
public class MotionBlur extends Module {

    public static MotionBlur INSTANCE = new MotionBlur();

    private final ModeSetting mode = new ModeSetting(
            "Режим",
            "TikTok RSMB",
            "TikTok RSMB",
            "Cinematic",
            "PvP Clean"
    );

    private final FloatSetting intensity = new FloatSetting("Интенсивность", 6.0f, 1.0f, 10.0f, 0.5f);

    private final BooleanSetting chromatic = new BooleanSetting("RGB Сплит (TikTok)", true);

    private final BooleanSetting smoothCamera = new BooleanSetting("Плавная камера", true);

    private Framebuffer helperFbo;

    private float lastYaw;
    private float lastPitch;
    private boolean initializedAngles;
    private float velX;
    private float velY;
    private float mouseVelX;
    private float mouseVelY;

    private double smoothMouseX;
    private double smoothMouseY;

    public MotionBlur() {
        super("MotionBlur", "Кинематографичное размытие и плавность камеры", ModuleCategory.RENDER);
        addSettings(mode, intensity, chromatic, smoothCamera);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        initializedAngles = false;
        velX = 0.0f;
        velY = 0.0f;
        mouseVelX = 0.0f;
        mouseVelY = 0.0f;
        smoothMouseX = 0.0;
        smoothMouseY = 0.0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        deleteHelperBuffer();
        initializedAngles = false;
        velX = 0.0f;
        velY = 0.0f;
        mouseVelX = 0.0f;
        mouseVelY = 0.0f;
        smoothMouseX = 0.0;
        smoothMouseY = 0.0;
    }

    public boolean shouldSmoothCamera() {
        return isEnable() && smoothCamera.isState();
    }

    public void onMouseDelta(double dx, double dy) {
        float factor = 0.0022f;
        mouseVelX += (float) -dx * factor;
        mouseVelY += (float) dy * factor;
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
            deleteHelperBuffer();
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

        if (mc.currentScreen != null) {
            return;
        }

        float yaw = camera != null ? camera.getYaw() : mc.player.getYaw();
        float pitch = camera != null ? camera.getPitch() : mc.player.getPitch();

        if (!initializedAngles) {
            lastYaw = yaw;
            lastPitch = pitch;
            initializedAngles = true;
            return;
        }

        float dy = yaw - lastYaw;
        float dp = pitch - lastPitch;
        if (dy > 180.0f) dy -= 360.0f;
        if (dy < -180.0f) dy += 360.0f;
        lastYaw = yaw;
        lastPitch = pitch;

        float targetVx = -dy * 0.0042f + mouseVelX;
        float targetVy = dp * 0.0042f + mouseVelY;

        mouseVelX *= 0.15f;
        mouseVelY *= 0.15f;

        velX = MathHelper.lerp(0.60f, velX, targetVx);
        velY = MathHelper.lerp(0.60f, velY, targetVy);

        if (Math.abs(targetVx) < 0.00005f && Math.abs(targetVy) < 0.00005f) {
            velX *= 0.25f;
            velY *= 0.25f;
        }

        float speed = (float) Math.hypot(velX, velY);
        if (speed < 0.00008f) {
            return;
        }

        ensureHelperBuffer(width, height);

        // 1. Копируем текущий 3D-кадр в helperFbo
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo.fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, helperFbo.fbo);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        // 2. Рисуем направленный шейдер MotionBlur обратно в mainFbo
        mainFbo.beginWrite(true);
        beginFullscreenState();
        try {
            ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.motionBlur);
            if (shader == null) return;

            GlUniform uVel = shader.getUniform("u_Velocity");
            GlUniform uInt = shader.getUniform("u_Intensity");
            GlUniform uChr = shader.getUniform("u_Chromatic");

            if (uVel != null) uVel.set(velX, velY);
            if (uInt != null) uInt.set(intensity.get());
            if (uChr != null) {
                boolean useChrom = chromatic.isState() && !mode.is("PvP Clean");
                uChr.set(useChrom ? 1.0f : 0.0f);
            }

            RenderSystem.setShader(ShaderUtils.motionBlur);
            RenderSystem.setShaderTexture(0, helperFbo.getColorAttachment());
            drawFullscreenQuad(1.0f);
        } finally {
            endFullscreenState();
            mainFbo.beginWrite(true);
        }
    }

    private void beginFullscreenState() {
        RenderSystem.backupProjectionMatrix();
        float scaledWidth = Math.max(1, mc.getWindow().getScaledWidth());
        float scaledHeight = Math.max(1, mc.getWindow().getScaledHeight());
        Matrix4f ortho = new Matrix4f().setOrtho(0f, scaledWidth, scaledHeight, 0f, -1000f, 1000f);
        RenderSystem.setProjectionMatrix(ortho, ProjectionType.ORTHOGRAPHIC);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
    }

    private void endFullscreenState() {
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, 0);
    }

    private void drawFullscreenQuad(float alpha) {
        float scaledWidth = Math.max(1, mc.getWindow().getScaledWidth());
        float scaledHeight = Math.max(1, mc.getWindow().getScaledHeight());
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(0, 0, 0).texture(0, 1).color(1f, 1f, 1f, alpha);
        buffer.vertex(0, scaledHeight, 0).texture(0, 0).color(1f, 1f, 1f, alpha);
        buffer.vertex(scaledWidth, scaledHeight, 0).texture(1, 0).color(1f, 1f, 1f, alpha);
        buffer.vertex(scaledWidth, 0, 0).texture(1, 1).color(1f, 1f, 1f, alpha);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void ensureHelperBuffer(int width, int height) {
        if (helperFbo == null || helperFbo.textureWidth != width || helperFbo.textureHeight != height) {
            deleteHelperBuffer();
            helperFbo = new SimpleFramebuffer(width, height, false);
            helperFbo.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            helperFbo.clear();
            RenderSystem.bindTexture(helperFbo.getColorAttachment());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            RenderSystem.bindTexture(0);
        }
    }

    private void deleteHelperBuffer() {
        if (helperFbo != null) {
            helperFbo.delete();
            helperFbo = null;
        }
    }
}
