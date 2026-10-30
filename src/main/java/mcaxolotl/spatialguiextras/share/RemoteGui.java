package mcaxolotl.spatialguiextras.share;

import java.util.UUID;

public final class RemoteGui {
    public record Pose(
            float offsetX,
            float offsetY,
            float offsetZ,
            float rightX,
            float rightY,
            float rightZ,
            float upX,
            float upY,
            float upZ,
            float halfWidth,
            float halfHeight,
            boolean curved,
            float arc,
            float radius,
            float halfHeightCylinder,
            float axisOffsetX,
            float axisOffsetY,
            float axisOffsetZ,
            float axisDirX,
            float axisDirY,
            float axisDirZ,
            float refDirX,
            float refDirY,
            float refDirZ) {}

    private OverlayPose overlay;
    private final RemoteGuiTexture overlayTexture = new RemoteGuiTexture();

    public RemoteGuiTexture overlayTexture() {
        return overlayTexture;
    }

    public void dispose() {
        texture.close();
        overlayTexture.close();
    }

    public void overlayVisual(OverlayVisual visual, long now) {
        overlay(visual == null ? null : visual.pose(), now);
        if (visual != null) {
            overlayTexture.resize(64, 64);
            overlayTexture.uploadTile(0, 0, 64, 64, visual.pixels());
        }
    }

    private long overlayTime;

    public void overlay(OverlayPose value, long now) {
        overlay = value;
        overlayTime = now;
    }

    public OverlayPose overlay() {
        return active && System.nanoTime() - overlayTime < 500_000_000L ? overlay : null;
    }

    private final UUID sender;
    private final RemoteGuiTexture texture = new RemoteGuiTexture();
    private volatile Pose pose;
    private volatile boolean active = true;
    private volatile long lastPacketNanos = System.nanoTime();

    public RemoteGui(UUID sender) {
        this.sender = sender;
    }

    public UUID sender() {
        return sender;
    }

    public RemoteGuiTexture texture() {
        return texture;
    }

    public Pose pose() {
        return pose;
    }

    public void pose(Pose pose) {
        this.pose = pose;
    }

    public boolean active() {
        return active;
    }

    private long closedNanos;
    private long session, closedSession;
    private AvatarPose avatarFrom, avatarTarget;
    private long avatarTime, avatarBlendNanos = 20_000_000L;
    private long avatarReleaseNanos;
    private static final long POSE_RETURN_NANOS = 250_000_000L;

    public void avatar(AvatarPose next, long now) {
        if (next == null) {
            releaseAvatar(now);
            return;
        }
        AvatarPose shown = avatar(now);
        avatarReleaseNanos = 0;
        avatarBlendNanos =
                avatarTime == 0
                        ? 20_000_000L
                        : Math.max(10_000_000L, Math.min(100_000_000L, now - avatarTime));
        avatarTime = now;
        avatarFrom = shown == null ? next : shown;
        avatarTarget = next;
    }

    private void releaseAvatar(long now) {
        if (avatarReleaseNanos != 0 || avatarTarget == null) return;
        AvatarPose shown = avatar(now);
        avatarFrom = avatarTarget = shown;
        avatarTime = now;
        avatarReleaseNanos = now;
    }

    public float avatarWeight(long now) {
        if (avatarTarget == null) return 0;
        if (avatarReleaseNanos != 0) {
            float t =
                    (float)
                            Math.max(
                                    0,
                                    Math.min(
                                            1,
                                            (now - avatarReleaseNanos)
                                                    / (double) POSE_RETURN_NANOS));
            return 1 - t * t * (3 - 2 * t);
        }
        if (!active) return 0;
        float stale =
                (float)
                        Math.max(
                                0,
                                Math.min(
                                        1,
                                        (now - avatarTime - 1_250_000_000L)
                                                / (double) POSE_RETURN_NANOS));
        return 1 - stale * stale * (3 - 2 * stale);
    }

    public AvatarPose avatar(long now) {
        if (avatarWeight(now) <= 0) return null;
        return avatarFrom == null
                ? avatarTarget
                : avatarFrom.blend(avatarTarget, (now - avatarTime) / (float) avatarBlendNanos);
    }

    private float opacity = 1;
    private int fadeMs = 300;

    public long session() {
        return session;
    }

    public boolean session(long next) {
        if (next < session || next <= closedSession) return false;
        if (next > session) {
            session = next;
            overlay = null;
            overlayTexture.close();
            tileFrames.clear();
            newestFrame = 0;
            texture.close();
            pose = null;
            avatarFrom = avatarTarget = null;
            avatarTime = 0;
            avatarReleaseNanos = 0;
            active = true;
        }
        return true;
    }

    public void animation(float opacity, int fadeMs) {
        this.opacity = opacity;
        this.fadeMs = fadeMs;
    }

    public float opacity() {
        return opacity;
    }

    public int fadeMs() {
        return fadeMs;
    }

    private long newestFrame;
    private final java.util.Map<Long, Long> tileFrames = new java.util.HashMap<>();

    public void clear() {
        if (active) closedNanos = System.nanoTime();
        releaseAvatar(System.nanoTime());
        active = false;
        closedSession = session;
    }

    public long closedNanos() {
        return closedNanos;
    }

    public void activate() {
        active = true;
        touch();
    }

    public boolean acceptTile(long frame, int fullWidth, int fullHeight, int x, int y) {
        if (frame < newestFrame && (fullWidth != texture.width() || fullHeight != texture.height()))
            return false;
        if (fullWidth != texture.width() || fullHeight != texture.height()) tileFrames.clear();
        long key = ((long) x << 32) | (y & 0xffffffffL);
        if (frame <= tileFrames.getOrDefault(key, -1L)) return false;
        newestFrame = Math.max(newestFrame, frame);
        tileFrames.put(key, frame);
        return true;
    }

    public void touch() {
        lastPacketNanos = System.nanoTime();
    }

    public long lastPacketNanos() {
        return lastPacketNanos;
    }

    public long millisSincePacket() {
        return (System.nanoTime() - lastPacketNanos) / 1_000_000L;
    }

    public void uploadTile(
            int fullWidth,
            int fullHeight,
            int x,
            int y,
            int tileWidth,
            int tileHeight,
            byte[] pixels) {
        texture.resize(fullWidth, fullHeight);
        texture.uploadTile(x, y, tileWidth, tileHeight, pixels);
    }
}
