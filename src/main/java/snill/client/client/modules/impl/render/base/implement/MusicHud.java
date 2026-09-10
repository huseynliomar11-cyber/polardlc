package snill.client.client.modules.impl.render.base.implement;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import snill.client.api.events.implement.EventRender;
import snill.client.api.utils.animation.AnimationUtils;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.draggable.Draggable;
import snill.client.api.utils.math.HoveringUtils;
import snill.client.api.utils.media.MediaTrack;
import snill.client.api.utils.media.MediaTracker;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.api.utils.scissor.ScissorUtils;
import snill.client.client.modules.impl.render.base.InterfaceProcessing;

/**
 * MusicHud "Aurora" — ультракомпактный музыкальный виджет:
 * масштаб 0.72x (-10% от предыдущего), увеличенная сочная волна (+10%),
 * идеально сбалансированные и синхронизированные отступы между волной, кнопками плеера и краями.
 */
public class MusicHud extends InterfaceProcessing {

    public static final float SCALE = 0.72f;

    private static final float CARD_W = 142.0f;
    private static final float CARD_H = 42.0f;

    private static final float COVER = 30.0f;
    private static final float COVER_X = 6.0f;
    private static final float COVER_Y = (CARD_H - COVER) * 0.5f; // 6.0f

    private static final float TEXT_X = 42.0f;
    private static final float CTRL_Y = 30.5f;

    // Синхронизированные отступы: обложка -> (7px) -> волна (45px) -> (7px) -> плеер (40px) -> (7px) -> край
    private static final float WAVE_X = 43.0f;
    private static final float WAVE_W = 45.0f;

    private static final float PREV_CX = 101.0f;
    private static final float PLAY_CX = 115.0f;
    private static final float NEXT_CX = 129.0f;

    private static final int EQ_BARS = 9;

    // Анимации
    private final AnimationUtils alphaAnim = new AnimationUtils(0.0f, 8.5f, Easings.QUAD_OUT);
    private final AnimationUtils progressAnim = new AnimationUtils(0.0f, 6.0f, Easings.QUAD_OUT);
    private final AnimationUtils playingAnim = new AnimationUtils(0.0f, 7.0f, Easings.QUAD_OUT);
    private final AnimationUtils cardHoverAnim = new AnimationUtils(0.0f, 10.0f, Easings.QUAD_OUT);
    private final AnimationUtils playHoverAnim = new AnimationUtils(0.0f, 12.0f, Easings.QUAD_OUT);
    private final AnimationUtils prevHoverAnim = new AnimationUtils(0.0f, 12.0f, Easings.QUAD_OUT);
    private final AnimationUtils nextHoverAnim = new AnimationUtils(0.0f, 12.0f, Easings.QUAD_OUT);
    private final AnimationUtils coverHoverAnim = new AnimationUtils(0.0f, 12.0f, Easings.QUAD_OUT);

    // Состояние
    private float discRotation;
    private float ringSpin;
    private float marqueePhase;
    private long lastFrameNs = System.nanoTime();
    private String lastTrackKey = "";

    public MusicHud(Draggable draggable) {
        super(draggable);
        MediaTracker.getInstance().start();
    }

    private Font font(int size) {
        Font f = Fonts.getFont("suisse", size);
        if (f == null) f = Fonts.getFont("sf_regular", size);
        return f;
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        MatrixStack matrices = eventRender.getContext().getMatrices();
        float x = draggable.getX();
        float y = draggable.getY();

        float scale = SCALE;
        draggable.setWidth(CARD_W * scale);
        draggable.setHeight(CARD_H * scale);

        MediaTracker tracker = MediaTracker.getInstance();
        tracker.updateVisualizer();
        MediaTrack track = tracker.getCurrentTrack();

        boolean inChat = mc.currentScreen instanceof ChatScreen;
        boolean hasTrack = track != null && (!track.getRawTitle().isEmpty() || track.isPlaying());
        boolean visible = hasTrack || inChat;

        alphaAnim.update(visible ? 1.0f : 0.0f);
        float alpha = alphaAnim.getValue();
        if (alpha <= 0.01f) return;

        long now = System.nanoTime();
        float dt = MathHelper.clamp((now - lastFrameNs) / 1_000_000_000.0f, 0.0f, 0.05f);
        lastFrameNs = now;

        boolean isPlaying = track != null && track.isPlaying();
        playingAnim.update(isPlaying ? 1.0f : 0.0f);
        float playing = playingAnim.getValue();

        if (isPlaying) {
            discRotation = (discRotation + dt * 95.0f) % 360.0f;
            ringSpin = (ringSpin + dt * 60.0f) % 360.0f;
        }

        String title = "Нет трека";
        String artist = "Включите музыку";
        boolean hasCover = false;

        if (track != null && !track.getRawTitle().isEmpty()) {
            title = track.getRawTitle();
            artist = track.getArtist();
            hasCover = track.hasCover();
        } else if (inChat) {
            title = "Music Player";
            artist = "Ожидание трека...";
        }

        String trackKey = title + "|" + artist;
        if (!trackKey.equals(lastTrackKey)) {
            lastTrackKey = trackKey;
            marqueePhase = 0.0f;
        }

        double mx = mc.mouse.getX() / mc.getWindow().getScaleFactor();
        double my = mc.mouse.getY() / mc.getWindow().getScaleFactor();
        boolean inScreen = mc.currentScreen != null;

        // Локальные координаты мыши с учётом масштаба
        double localMx = (mx - x) / scale;
        double localMy = (my - y) / scale;

        boolean cardHovered = inScreen && HoveringUtils.isHovered(localMx, localMy, 0, 0, CARD_W, CARD_H);
        boolean coverHovered = inScreen && HoveringUtils.isHovered(localMx, localMy, COVER_X, COVER_Y, COVER, COVER);
        boolean playHovered = inScreen && HoveringUtils.isHovered(localMx, localMy, PLAY_CX - 8.0f, CTRL_Y - 8.0f, 16.0f, 16.0f);
        boolean prevHovered = inScreen && HoveringUtils.isHovered(localMx, localMy, PREV_CX - 7.0f, CTRL_Y - 7.0f, 14.0f, 14.0f);
        boolean nextHovered = inScreen && HoveringUtils.isHovered(localMx, localMy, NEXT_CX - 7.0f, CTRL_Y - 7.0f, 14.0f, 14.0f);

        cardHoverAnim.update(cardHovered ? 1.0f : 0.0f);
        coverHoverAnim.update(coverHovered ? 1.0f : 0.0f);
        playHoverAnim.update(playHovered ? 1.0f : 0.0f);
        prevHoverAnim.update(prevHovered ? 1.0f : 0.0f);
        nextHoverAnim.update(nextHovered ? 1.0f : 0.0f);

        int theme = ColorUtils.getThemeColor();
        int themeSoft = ColorUtils.darken(theme, 0.35f);
        int textColor = ColorUtils.applyAlpha(ColorUtils.rgba(246, 247, 252, 255), alpha);
        int subColor = ColorUtils.applyAlpha(ColorUtils.rgba(150, 155, 172, 255), alpha);

        float pulse = 0.45f + 0.55f * (float) ((Math.sin(System.currentTimeMillis() * 0.0058) + 1.0) * 0.5);
        pulse = 0.35f + pulse * 0.65f * playing;

        // Применяем масштабирование матрицы
        matrices.push();
        matrices.translate(x, y, 0.0f);
        matrices.scale(scale, scale, 1.0f);

        // ================= КАРТОЧКА =================
        drawCardBase(matrices, 0, 0, theme, themeSoft, alpha, pulse);

        // ================= ОБЛОЖКА / ВИНИЛ =================
        float coverCx = COVER_X + COVER * 0.5f;
        float coverCy = COVER_Y + COVER * 0.5f;
        drawCoverOrVinyl(matrices, track, coverCx, coverCy, hasCover, isPlaying, theme, alpha);
        drawCoverProgressRing(matrices, track, coverCx, coverCy, theme, alpha);
        drawCoverHoverOverlay(matrices, coverCx, coverCy, isPlaying, alpha);

        // ================= ТЕКСТ =================
        Font titleFont = font(11);
        Font artistFont = font(9);
        float textRight = CARD_W - 8.0f;
        float maxTextW = Math.max(20.0f, textRight - TEXT_X);

        drawMarquee(titleFont, matrices, title, TEXT_X, 5.0f, maxTextW, textColor, dt, isPlaying, x, y, scale);
        drawArtistLine(artistFont, matrices, artist, TEXT_X, 15.6f, maxTextW, subColor, theme, alpha, isPlaying);

        // ================= УВЕЛИЧЕННЫЙ ЭКВАЛАЙЗЕР (+10%) =================
        drawEqualizer(matrices, tracker, WAVE_X, CTRL_Y, theme, alpha, isPlaying);

        // ================= ПЛЕЕР (СИНХРОНИЗИРОВАННЫЙ БЛОК УПРАВЛЕНИЯ) =================
        drawPrevButton(matrices, PREV_CX, CTRL_Y, theme, alpha, prevHoverAnim.getValue());
        drawPlayButton(matrices, PLAY_CX, CTRL_Y, isPlaying, theme, alpha);
        drawNextButton(matrices, NEXT_CX, CTRL_Y, theme, alpha, nextHoverAnim.getValue());

        // ================= НИЖНЯЯ ЛИНИЯ ПРОГРЕССА =================
        drawBottomProgress(matrices, track, 0, 0, theme, alpha);

        matrices.pop();

        super.onRender(eventRender);
    }

    private void drawCardBase(MatrixStack matrices, float x, float y, int theme, int themeSoft, float alpha, float pulse) {
        RenderUtils.drawShadow(matrices, x - 1.5f, y - 1.0f, CARD_W + 3.0f, CARD_H + 3.0f, 10.0f, 15.0f,
                ColorUtils.applyAlpha(theme, alpha * 0.16f * pulse));

        RenderUtils.drawRoundedRect(matrices, x, y, CARD_W, CARD_H, 10.0f,
                ColorUtils.rgba(7, 8, 13, (int) (232 * alpha)));

        RenderUtils.drawGradientRect(matrices, x, y, CARD_W, CARD_H, 10.0f,
                ColorUtils.rgba(26, 28, 40, (int) (54 * alpha)),
                ColorUtils.rgba(8, 9, 14, (int) (14 * alpha)));

        float hover = cardHoverAnim.getValue();
        int border = ColorUtils.interpolateColor(
                ColorUtils.rgba(255, 255, 255, 14),
                theme,
                hover * 0.55f
        );
        RenderUtils.drawRoundedRectOutline(matrices, x, y, CARD_W, CARD_H, 10.0f, 0.9f,
                ColorUtils.applyAlpha(border, alpha));

        // Неоновые грани сверху и снизу
        RenderUtils.drawRoundedRect(matrices, x + 12.0f, y, 44.0f, 1.0f, 0.5f,
                ColorUtils.applyAlpha(theme, alpha * 0.85f));

        RenderUtils.drawRoundedRect(matrices, x + CARD_W - 56.0f, y + CARD_H - 1.0f, 44.0f, 1.0f, 0.5f,
                ColorUtils.applyAlpha(themeSoft, alpha * 0.45f));
    }

    private void drawCoverOrVinyl(MatrixStack matrices, MediaTrack track, float cx, float cy,
                                  boolean hasCover, boolean isPlaying, int theme, float alpha) {
        RenderUtils.drawShadow(matrices, cx - COVER * 0.5f, cy - COVER * 0.5f, COVER, COVER, 8.0f, 9.0f,
                ColorUtils.applyAlpha(theme, alpha * (0.18f + 0.22f * playingAnim.getValue())));

        if (hasCover && track != null) {
            RenderUtils.drawRoundedImage(matrices, track.getCoverTexture(),
                    cx - COVER * 0.5f, cy - COVER * 0.5f, COVER, 8.0f, alpha);
            RenderUtils.drawRoundedRect(matrices, cx - COVER * 0.5f, cy - COVER * 0.5f, COVER, COVER, 8.0f,
                    ColorUtils.rgba(255, 255, 255, (int) (12 * alpha)));
        } else {
            drawVinyl(matrices, cx, cy, COVER * 0.5f, theme, alpha, isPlaying);
        }
    }

    private void drawVinyl(MatrixStack matrices, float cx, float cy, float r, int theme, float alpha, boolean isPlaying) {
        RenderUtils.drawRoundCircle(matrices, cx, cy, r * 2.0f,
                ColorUtils.applyAlpha(ColorUtils.rgba(9, 9, 13, 255), alpha));
        RenderUtils.drawRoundCircle(matrices, cx, cy, r * 2.0f - 2.0f,
                ColorUtils.applyAlpha(ColorUtils.rgba(24, 24, 32, 255), alpha));

        for (int i = 0; i < 3; i++) {
            float groove = r - 2.4f - i * 2.4f;
            if (groove < 5.0f) break;
            RenderUtils.drawRoundCircle(matrices, cx, cy, groove * 2.0f,
                    ColorUtils.applyAlpha(ColorUtils.rgba(36 + i * 6, 36, 46, 255), alpha));
            RenderUtils.drawRoundCircle(matrices, cx, cy, groove * 2.0f - 1.6f,
                    ColorUtils.applyAlpha(ColorUtils.rgba(14, 14, 20, 255), alpha));
        }

        RenderUtils.drawRoundCircle(matrices, cx, cy, 10.0f, ColorUtils.applyAlpha(theme, alpha));
        RenderUtils.drawRoundCircle(matrices, cx, cy, 3.6f,
                ColorUtils.applyAlpha(ColorUtils.rgba(8, 8, 12, 255), alpha));

        float orbitR = r - 4.6f;
        double rad = Math.toRadians(discRotation);
        float dotX = cx + (float) Math.cos(rad) * orbitR;
        float dotY = cy + (float) Math.sin(rad) * orbitR;
        RenderUtils.drawRoundCircle(matrices, dotX, dotY, 2.2f,
                ColorUtils.applyAlpha(ColorUtils.rgba(255, 255, 255, 255), alpha * (0.35f + 0.45f * playingAnim.getValue())));

        RenderUtils.drawRingArc(matrices, cx - r, cy - r, r * 2.0f, 1.2f, -50.0f, 22.0f,
                ColorUtils.applyAlpha(ColorUtils.rgba(255, 255, 255, 255), alpha * 0.28f));
    }

    private void drawCoverProgressRing(MatrixStack matrices, MediaTrack track, float cx, float cy, int theme, float alpha) {
        float size = COVER + 3.8f;
        float rx = cx - size * 0.5f;
        float ry = cy - size * 0.5f;
        float thickness = 1.6f;
        float radius = size * 0.5f;

        RenderUtils.drawRingArc(matrices, rx, ry, size, 0.9f, 0.0f, 360.0f,
                ColorUtils.rgba(255, 255, 255, (int) (16 * alpha)));

        float target = track != null ? track.getProgress() : 0.0f;
        progressAnim.update(target);
        float prog = MathHelper.clamp(progressAnim.getValue(), 0.0f, 1.0f);

        float arc;
        float startAngle = -90.0f;
        if (track != null && track.getDurationMs() > 0) {
            arc = Math.max(6.0f, prog * 360.0f);
        } else if (track != null && track.isPlaying()) {
            startAngle = ringSpin;
            arc = 90.0f;
        } else {
            return;
        }

        RenderUtils.drawRingArc(matrices, rx, ry, size, thickness, startAngle, startAngle + arc,
                ColorUtils.applyAlpha(theme, alpha * 0.95f));

        double tipRad = Math.toRadians(startAngle + arc);
        float midR = radius - thickness * 0.5f;
        float tipX = cx + (float) Math.cos(tipRad) * midR;
        float tipY = cy + (float) Math.sin(tipRad) * midR;
        RenderUtils.drawRoundCircle(matrices, tipX, tipY, 4.2f,
                ColorUtils.applyAlpha(theme, alpha * 0.35f));
        RenderUtils.drawRoundCircle(matrices, tipX, tipY, 2.4f,
                ColorUtils.applyAlpha(ColorUtils.interpolateColor(theme, ColorUtils.rgba(255, 255, 255, 255), 0.6f), alpha));
    }

    private void drawCoverHoverOverlay(MatrixStack matrices, float cx, float cy, boolean isPlaying, float alpha) {
        float hover = coverHoverAnim.getValue();
        if (hover <= 0.02f) return;

        RenderUtils.drawRoundCircle(matrices, cx, cy, COVER,
                ColorUtils.rgba(5, 6, 10, (int) (150 * alpha * hover)));

        int icon = ColorUtils.applyAlpha(ColorUtils.rgba(255, 255, 255, 255), alpha * hover);
        if (isPlaying) {
            RenderUtils.drawRoundedRect(matrices, cx - 2.2f, cy - 3.2f, 1.5f, 6.4f, 0.5f, icon);
            RenderUtils.drawRoundedRect(matrices, cx + 0.7f, cy - 3.2f, 1.5f, 6.4f, 0.5f, icon);
        } else {
            drawPlayIcon(matrices, cx + 0.3f, cy, icon);
        }
    }

    private void drawMarquee(Font font, MatrixStack matrices, String text, float localX, float localY,
                             float maxW, int color, float dt, boolean isPlaying, float worldX, float worldY, float scale) {
        if (font == null || text == null || text.isEmpty()) return;

        float width = font.getWidth(text);
        float overflow = width - maxW;
        if (overflow <= 0.5f) {
            font.draw(matrices, text, localX, localY, color);
            return;
        }

        marqueePhase += dt * (isPlaying ? 1.1f : 0.55f);
        float offset = overflow * (0.5f - 0.5f * (float) Math.cos(marqueePhase));

        ScissorUtils.push();
        ScissorUtils.setFromComponentCoordinates(
                worldX + localX * scale,
                worldY + (localY - 2.0f) * scale,
                maxW * scale,
                11.0f * scale
        );
        try {
            font.draw(matrices, text, localX - offset, localY, color);
        } finally {
            ScissorUtils.unset();
            ScissorUtils.pop();
        }
    }

    private void drawArtistLine(Font font, MatrixStack matrices, String artist, float x, float y,
                                float maxW, int subColor, int theme, float alpha, boolean isPlaying) {
        float dotPulse = isPlaying
                ? 0.55f + 0.45f * (float) ((Math.sin(System.currentTimeMillis() * 0.008) + 1.0) * 0.5)
                : 0.3f;
        int dotColor = isPlaying
                ? ColorUtils.applyAlpha(theme, alpha * dotPulse)
                : ColorUtils.applyAlpha(ColorUtils.rgba(110, 114, 130, 255), alpha * 0.7f);

        RenderUtils.drawRoundCircle(matrices, x + 2.0f, y + 4.2f, 4.4f,
                ColorUtils.applyAlpha(theme, alpha * dotPulse * 0.30f));
        RenderUtils.drawRoundCircle(matrices, x + 2.0f, y + 4.2f, 2.6f, dotColor);

        if (font != null) {
            font.draw(matrices, ellipsize(font, artist, maxW - 10.0f), x + 8.5f, y, subColor);
        }
    }

    /**
     * Увеличенная на 10% волна эквалайзера (высота 13.0f, ширина бара 2.8f).
     * Точно вписана в диапазон от WAVE_X (43px) до 88px (ширина 45px).
     */
    private void drawEqualizer(MatrixStack matrices, MediaTracker tracker, float startX, float baseY,
                               int theme, float alpha, boolean isPlaying) {
        float[] bars = tracker.getVisualizerBars();
        if (bars == null || bars.length == 0) return;

        float barW = 2.8f;
        float gap = 2.475f;
        float maxH = 13.0f; // +13% высоты волны
        long time = System.currentTimeMillis();

        for (int i = 0; i < EQ_BARS; i++) {
            float src = bars[Math.min(i, bars.length - 1)];
            float breathe = isPlaying ? 0.12f * (float) Math.sin(time * 0.011 + i * 0.8) : 0.0f;
            float value = MathHelper.clamp(src + breathe, 0.12f, 1.0f);
            float h = Math.max(2.2f, value * maxH);

            float bx = startX + i * (barW + gap);
            float bottom = baseY + 4.5f;

            // Glow-подложка
            RenderUtils.drawRoundedRect(matrices, bx - 0.8f, bottom - h - 0.8f, barW + 1.6f, h + 1.6f,
                    (barW + 1.6f) * 0.5f,
                    ColorUtils.applyAlpha(theme, alpha * 0.18f * value));

            // Капсула
            int top = ColorUtils.interpolateColor(theme, ColorUtils.rgba(255, 255, 255, 255), 0.55f);
            int bottomC = ColorUtils.darken(theme, 0.22f);
            RenderUtils.drawGradientRect(matrices, bx, bottom - h, barW, h, barW * 0.5f,
                    ColorUtils.applyAlpha(top, alpha * 0.95f),
                    ColorUtils.applyAlpha(bottomC, alpha * 0.80f));

            // Светящаяся неоновая точка на вершине
            RenderUtils.drawRoundCircle(matrices, bx + barW * 0.5f, bottom - h, barW * 1.15f,
                    ColorUtils.applyAlpha(ColorUtils.rgba(255, 255, 255, 255), alpha * 0.60f * value));
        }
    }

    private void drawPrevButton(MatrixStack matrices, float cx, float cy, int theme, float alpha, float hover) {
        if (hover > 0.01f) {
            RenderUtils.drawRoundCircle(matrices, cx, cy, 12.0f,
                    ColorUtils.applyAlpha(theme, alpha * 0.14f * hover));
            RenderUtils.drawRoundCircle(matrices, cx, cy, 10.0f,
                    ColorUtils.rgba(255, 255, 255, (int) (18 * alpha * hover)));
        }

        int baseColor = ColorUtils.rgba(165, 170, 190, 255);
        int activeColor = ColorUtils.interpolateColor(baseColor, theme, hover);
        int col = ColorUtils.applyAlpha(activeColor, alpha * (0.55f + 0.45f * hover));

        // Вертикальная черта слева
        RenderUtils.drawRoundedRect(matrices, cx - 3.4f, cy - 3.2f, 1.2f, 6.4f, 0.5f, col);

        // Стрелка влево
        drawTriangle(matrices, cx + 2.8f, cy - 3.2f, cx - 1.4f, cy, cx + 2.8f, cy + 3.2f, col);
    }

    private void drawNextButton(MatrixStack matrices, float cx, float cy, int theme, float alpha, float hover) {
        if (hover > 0.01f) {
            RenderUtils.drawRoundCircle(matrices, cx, cy, 12.0f,
                    ColorUtils.applyAlpha(theme, alpha * 0.14f * hover));
            RenderUtils.drawRoundCircle(matrices, cx, cy, 10.0f,
                    ColorUtils.rgba(255, 255, 255, (int) (18 * alpha * hover)));
        }

        int baseColor = ColorUtils.rgba(165, 170, 190, 255);
        int activeColor = ColorUtils.interpolateColor(baseColor, theme, hover);
        int col = ColorUtils.applyAlpha(activeColor, alpha * (0.55f + 0.45f * hover));

        // Стрелка вправо
        drawTriangle(matrices, cx - 2.8f, cy - 3.2f, cx + 1.4f, cy, cx - 2.8f, cy + 3.2f, col);

        // Вертикальная черта справа
        RenderUtils.drawRoundedRect(matrices, cx + 2.2f, cy - 3.2f, 1.2f, 6.4f, 0.5f, col);
    }

    private void drawTriangle(MatrixStack matrices, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        float[] rgba = unpack(color);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, x1, y1, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.vertex(matrix, x2, y2, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.vertex(matrix, x3, y3, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    private void drawPlayButton(MatrixStack matrices, float cx, float cy, boolean isPlaying, int theme, float alpha) {
        float hover = playHoverAnim.getValue();

        float spin = (System.currentTimeMillis() * 0.09f) % 360.0f;
        float ringD = 16.0f;
        float rx = cx - ringD * 0.5f;
        float ry = cy - ringD * 0.5f;

        if (isPlaying) {
            RenderUtils.drawRingArc(matrices, rx, ry, ringD, 1.1f, spin, spin + 70.0f,
                    ColorUtils.applyAlpha(theme, alpha * (0.60f + 0.40f * hover)));
            RenderUtils.drawRingArc(matrices, rx, ry, ringD, 1.1f, spin + 180.0f, spin + 250.0f,
                    ColorUtils.applyAlpha(theme, alpha * (0.40f + 0.30f * hover)));
        } else {
            RenderUtils.drawRingArc(matrices, rx, ry, ringD, 1.0f, 0.0f, 360.0f,
                    ColorUtils.rgba(255, 255, 255, (int) ((20 + 35 * hover) * alpha)));
        }

        RenderUtils.drawRoundCircle(matrices, cx, cy, 13.0f,
                ColorUtils.applyAlpha(theme, alpha * (0.08f + 0.20f * hover)));

        int core = ColorUtils.interpolateColor(
                ColorUtils.rgba(14, 15, 23, (int) (240 * alpha)),
                ColorUtils.rgba(32, 34, 48, (int) (245 * alpha)),
                hover
        );
        RenderUtils.drawRoundCircle(matrices, cx, cy, 10.5f, core);

        int icon = ColorUtils.interpolateColor(theme, ColorUtils.rgba(255, 255, 255, 255), hover);
        icon = ColorUtils.applyAlpha(icon, alpha);

        if (isPlaying) {
            RenderUtils.drawRoundedRect(matrices, cx - 1.8f, cy - 2.6f, 1.2f, 5.2f, 0.4f, icon);
            RenderUtils.drawRoundedRect(matrices, cx + 0.6f, cy - 2.6f, 1.2f, 5.2f, 0.4f, icon);
        } else {
            drawPlayIcon(matrices, cx + 0.3f, cy, icon);
        }
    }

    private void drawBottomProgress(MatrixStack matrices, MediaTrack track, float x, float y, int theme, float alpha) {
        float lineX = x + 14.0f;
        float lineY = y + CARD_H - 1.2f;
        float lineW = CARD_W - 28.0f;

        RenderUtils.drawRoundedRect(matrices, lineX, lineY, lineW, 1.2f, 0.6f,
                ColorUtils.rgba(255, 255, 255, (int) (14 * alpha)));

        float prog = MathHelper.clamp(progressAnim.getValue(), 0.0f, 1.0f);
        if (prog <= 0.001f) return;

        float fillW = lineW * prog;
        RenderUtils.drawRoundedRect(matrices, lineX, lineY, fillW, 1.2f, 0.6f,
                ColorUtils.applyAlpha(theme, alpha * 0.90f));

        float headX = lineX + fillW;
        RenderUtils.drawRoundCircle(matrices, headX, lineY + 0.6f, 3.2f,
                ColorUtils.applyAlpha(ColorUtils.rgba(255, 255, 255, 255), alpha * 0.95f));
    }

    private String ellipsize(Font font, String text, float maxW) {
        if (font == null || text == null) return "";
        if (font.getWidth(text) <= maxW) return text;

        String value = text;
        while (font.getWidth(value + "...") > maxW && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "...";
    }

    private void drawPlayIcon(MatrixStack matrices, float cx, float cy, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        float[] rgba = unpack(color);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, cx - 1.4f, cy - 2.5f, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.vertex(matrix, cx - 1.4f, cy + 2.5f, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.vertex(matrix, cx + 2.3f, cy, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    private float[] unpack(int color) {
        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        return new float[]{
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f,
                a / 255.0f
        };
    }

    public boolean handleClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        float x = draggable.getX();
        float y = draggable.getY();
        float scale = SCALE;

        double localMx = (mouseX - x) / scale;
        double localMy = (mouseY - y) / scale;

        // Кнопка Play/Pause
        if (HoveringUtils.isHovered(localMx, localMy, PLAY_CX - 8.0f, CTRL_Y - 8.0f, 16.0f, 16.0f)) {
            MediaTracker.getInstance().togglePlayPause();
            return true;
        }

        // Предыдущий трек
        if (HoveringUtils.isHovered(localMx, localMy, PREV_CX - 7.0f, CTRL_Y - 7.0f, 14.0f, 14.0f)) {
            MediaTracker.getInstance().prevTrack();
            return true;
        }

        // Следующий трек
        if (HoveringUtils.isHovered(localMx, localMy, NEXT_CX - 7.0f, CTRL_Y - 7.0f, 14.0f, 14.0f)) {
            MediaTracker.getInstance().nextTrack();
            return true;
        }

        // Клик по обложке (также пауза/плей)
        if (HoveringUtils.isHovered(localMx, localMy, COVER_X, COVER_Y, COVER, COVER)) {
            MediaTracker.getInstance().togglePlayPause();
            return true;
        }

        return false;
    }
}
