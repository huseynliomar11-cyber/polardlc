package snill.client.client.modules.impl.render;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import org.joml.Matrix4f;
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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import snill.client.api.utils.render.ShaderUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

@Getter
public class MotionBlur extends Module {

    public static MotionBlur INSTANCE = new MotionBlur();

    private final ModeSetting mode = new ModeSetting(
            "Режим",
            "Комбо",
            "Комбо",
            "Динамический",
            "Плавная камера"
    );

    private final FloatSetting intensity = new FloatSetting("Интенсивность", 5.0f, 1.0f, 10.0f, 0.5f);

    private Framebuffer helperFbo;

    private float lastYaw;
    private float lastPitch;
    private boolean initializedAngles;
    private float velX;
    private float velY;

    private double smoothMouseX;
    private double smoothMouseY;

    public MotionBlur() {
        super("MotionBlur", "Кинематографичное размытие и плавность камеры", ModuleCategory.RENDER);
        addSettings(mode, intensity);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        initializedAngles = false;
        velX = 0.0f;
        velY = 0.0f;
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
        smoothMouseX = 0.0;
        smoothMouseY = 0.0;
    }

    private void deleteHelperBuffer() {
        if (helperFbo != null) {
            helperFbo.delete();
            helperFbo = null;
        }
    }

    public boolean shouldRenderShaderBlur() {
        return mode.is("Комбо") || mode.is("Динамический");
    }

    public boolean shouldSmoothCamera() {
        return mode.is("Комбо") || mode.is("Плавная камера");
    }

    /**
     * Сглаживание микро-рывков мыши без ватной инерции F8:
     * когда мышь двигается — сглаживает ступеньки сенсора,
     * когда мышь остановилась — моментально сбрасывает скорость, не отставая от руки.
     */
    public double smoothMouse(double target, boolean isX) {
        if (!isEnable() || !shouldSmoothCamera()) {
            return target;
        }

        // При высоком значении интенсивности сглаживание плотнее
        double factor = MathHelper.clamp(0.40 + (intensity.get() / 10.0) * 0.35, 0.40, 0.75);

        if (isX) {
            if (Math.abs(target) < 0.0001) {
                smoothMouseX *= 0.20; // Моментальное гашение инерции
                if (Math.abs(smoothMouseX) < 0.01) smoothMouseX = 0.0;
                return smoothMouseX;
            }
            smoothMouseX = smoothMouseX + (target - smoothMouseX) * factor;
            return smoothMouseX;
        } else {
            if (Math.abs(target) < 0.0001) {
                smoothMouseY *= 0.20; // Моментальное гашение инерции
                if (Math.abs(smoothMouseY) < 0.01) smoothMouseY = 0.0;
                return smoothMouseY;
            }
            smoothMouseY = smoothMouseY + (target - smoothMouseY) * factor;
            return smoothMouseY;
        }
    }

    /**
     * Шейдерное направленное размытие по текущей угловой скорости камеры:
     * размывает текущий кадр строго в направлении вращения,
     * при остановке камеры скорость = 0, поэтому размытие исчезает мгновенно (не отстает от камеры).
     */
    public void applyMotionBlur(Camera camera) {
        if (!isEnable() || !shouldRenderShaderBlur()) {
            return;
        }

        if (camera == null || mc.player == null || mc.world == null) {
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

        float yaw = camera.getYaw();
        float pitch = camera.getPitch();

        if (!initializedAngles) {
            lastYaw = yaw;
            lastPitch = pitch;
            initializedAngles = true;
            return;
        }

        float dy = yaw - lastYaw;
        float dp = pitch - lastPitch;
        lastYaw = yaw;
        lastPitch = pitch;

        if (dy > 180.0f) dy -= 360.0f;
        if (dy < -180.0f) dy += 360.0f;

        // Вектор скорости поворота в пространстве экрана
        float targetVx = -dy * 0.0032f;
        float targetVy = dp * 0.0032f;

        velX = MathHelper.lerp(0.65f, velX, targetVx);
        velY = MathHelper.lerp(0.65f, velY, targetVy);

        float speed = (float) Math.hypot(velX, velY);
        if (speed < 0.00008f) {
            return; // Камера стоит на месте — размытие не требуется
        }

        ensureHelperBuffer(width, height);

        // 1. Аппаратное быстрое копирование текущего кадра в промежуточный буфер
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo.fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, helperFbo.fbo);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        // 2. Отрисовка направленного блюра обратно в mainFbo через шейдер
        mainFbo.beginWrite(true);

        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.motionBlur);
        GlUniform velUniform = shader.getUniform("u_Velocity");
        GlUniform intensityUniform = shader.getUniform("u_Intensity");

        if (velUniform != null) {
            velUniform.set(velX, velY);
        }
        if (intensityUniform != null) {
            intensityUniform.set(intensity.get());
        }

        RenderSystem.backupProjectionMatrix();
        try {
            Matrix4f identity = new Matrix4f().identity();
            RenderSystem.setProjectionMatrix(identity, ProjectionType.ORTHOGRAPHIC);

            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();

            RenderSystem.setShader(ShaderUtils.motionBlur);
            RenderSystem.setShaderTexture(0, helperFbo.getColorAttachment());

            BufferBuilder buffer = Tessellator.getInstance().begin(
                    VertexFormat.DrawMode.QUADS,
                    VertexFormats.POSITION_TEXTURE
            );
            buffer.vertex(-1.0f, -1.0f, 0.0f).texture(0.0f, 0.0f);
            buffer.vertex(-1.0f, 1.0f, 0.0f).texture(0.0f, 1.0f);
            buffer.vertex(1.0f, 1.0f, 0.0f).texture(1.0f, 1.0f);
            buffer.vertex(1.0f, -1.0f, 0.0f).texture(1.0f, 0.0f);
            BufferRenderer.drawWithGlobalProgram(buffer.end());

            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
        } finally {
            RenderSystem.restoreProjectionMatrix();
        }

        mainFbo.beginWrite(true);
    }

    private void ensureHelperBuffer(int width, int height) {
        if (helperFbo == null || helperFbo.textureWidth != width || helperFbo.textureHeight != height) {
            deleteHelperBuffer();
            helperFbo = new SimpleFramebuffer(width, height, false);
            helperFbo.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            helperFbo.clear();
        }
    }
}
