package snill.client.client.modules.impl.movement;

import net.minecraft.util.math.Vec3d;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.storages.implement.RotationStorage;
import snill.client.api.utils.math.TimerUtils;
import snill.client.api.utils.rotate.Rotation;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.ModeSetting;

import java.util.concurrent.ThreadLocalRandom;
import ru.virtuoz.convert.Convert;

@Convert(Convert.ConvertType.MUTATION)
public class GrimGlide extends Module {

    public static GrimGlide INSTANCE = new GrimGlide();

    private static final double EVEN_TICK_SPEED = 0.087;
    private static final double ODD_TICK_SPEED = 0.09;
    private static final double VERTICAL_BOOST = 0.00600000075995922;
    private static final double MIN_VELOCITY_MULTIPLIER = 1.001;
    private static final double MAX_VELOCITY_MULTIPLIER = 1.0021;
    private static final long VELOCITY_UPDATE_DELAY = 40L;

    private final ModeSetting mode = new ModeSetting("Режим", "CakeWorld", "CakeWorld");
    private final TimerUtils timer = new TimerUtils();
    private long ticksTwo = 0;

    public GrimGlide() {
        super("GrimGlide", "[Rage] Экспериментальный глайд на элитрах (Небезопасно для GrimAC)", ModuleCategory.MOVEMENT);
        addSettings(mode);
    }

    @EventLink
    @Convert(Convert.ConvertType.ULTRA)
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null || !mc.player.isGliding()) {
            return;
        }

        switch (mode.getCurrent()) {
            case "CakeWorld" -> handleCakeWorld();
        }
    }

    private void handleCakeWorld() {
        ticksTwo++;

        float yaw = resolveYaw();
        MovementVector movement = calculateMovement(yaw, mc.player.age);

        movePlayer(movement);
        updateVelocityIfReady(movement);
    }

    @Override
    @Convert(Convert.ConvertType.ULTRA)
    public void onDisable() {
        timer.reset();
        ticksTwo = 0;
        super.onDisable();
    }

    @Convert(Convert.ConvertType.ULTRA)
    public long getTicksTwo() {
        return ticksTwo;
    }

    @Convert(Convert.ConvertType.ULTRA)
    private float resolveYaw() {
        RotationStorage storage = RotationStorage.instance;
        if (storage != null && storage.isRotating()) {
            Rotation rotation = storage.targetRotation();
            if (rotation != null) {
                return rotation.getYaw();
            }
        }
        return mc.player.getYaw();
    }

    @Convert(Convert.ConvertType.ULTRA)
    private void movePlayer(MovementVector movement) {
        mc.player.updatePosition(
                mc.player.getX() + movement.dx,
                mc.player.getY(),
                mc.player.getZ() + movement.dz
        );
    }

    @Convert(Convert.ConvertType.ULTRA)
    private void updateVelocityIfReady(MovementVector movement) {
        if (!timer.finished(VELOCITY_UPDATE_DELAY)) {
            return;
        }

        Vec3d currentVelocity = mc.player.getVelocity();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        double xMultiplier = random.nextDouble(MIN_VELOCITY_MULTIPLIER, MAX_VELOCITY_MULTIPLIER);
        double zMultiplier = random.nextDouble(MIN_VELOCITY_MULTIPLIER, MAX_VELOCITY_MULTIPLIER);

        mc.player.setVelocity(new Vec3d(
                movement.dx * xMultiplier,
                currentVelocity.y + VERTICAL_BOOST,
                movement.dz * zMultiplier
        ));

        timer.reset();
    }

    @Convert(Convert.ConvertType.ULTRA)
    private static MovementVector calculateMovement(float yaw, int playerAge) {
        double speed = playerAge % 2 == 0 ? EVEN_TICK_SPEED : ODD_TICK_SPEED;
        return MovementVector.fromYaw(yaw, speed);
    }

    private static final class MovementVector {
        private final double dx;
        private final double dz;

        private MovementVector(double dx, double dz) {
            this.dx = dx;
            this.dz = dz;
        }

        private static MovementVector fromYaw(float yaw, double speed) {
            double radians = Math.toRadians(yaw);
            return new MovementVector(
                    -Math.sin(radians) * speed,
                    Math.cos(radians) * speed
            );
        }
    }
}
