package mcaxolotl.spatialguiextras.share;

import mcaxolotl.spatialguiextras.ExtrasClient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_2540;
import net.minecraft.class_310;
import net.minecraft.class_742;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;

public final class ShareClient implements ClientModInitializer {
    private static final Map<UUID, RemoteGui> REMOTES = new HashMap<>();
    private static final ExecutorService COMPRESSOR =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "Spatial-GUI-Extras-Compressor");
                        t.setDaemon(true);
                        return t;
                    });

    private record Tile(
            long session,
            long frame,
            int width,
            int height,
            int x,
            int y,
            int tw,
            int th,
            byte[] data) {}

    private record TileKey(UUID sender, int x, int y) {}

    private record Update(UUID sender, ShareProtocol.Message message) {}

    private static final LatestQueue<Integer, Tile> READY = new LatestQueue<>(16384);
    private static final LatestQueue<TileKey, Update> INCOMING = new LatestQueue<>(8192);
    private static final Map<UUID, ShareProtocol.Message> POSES = new ConcurrentHashMap<>(),
            AVATARS = new ConcurrentHashMap<>(),
            CARRIED = new ConcurrentHashMap<>();
    private static final AckAccumulator ACKS = new AckAccumulator(),
            CONTROLS = new AckAccumulator();
    private static final StreamSessions SESSIONS = new StreamSessions();
    private static volatile UUID ownId;
    private static final LatestQueue<UUID, Update> CLEARS =
            new LatestQueue<>(StreamSessions.MAX_SENDERS);
    private static final FlowWindow FLOW = new FlowWindow();
    private static final AtomicInteger QUEUED = new AtomicInteger(),
            RECEIVED = new AtomicInteger(),
            INVALID = new AtomicInteger();
    private static final CRC32 CRC = new CRC32();
    private static volatile long generation = 1;
    private static long frameId = 1,
            lastCapture,
            lastPose,
            lastKeyframe,
            bandwidthTime,
            statsTime,
            lastWarning,
            lastPrune,
            lastPublishedSession;
    private static int[] hashes;
    private static int width, height, tilesX, tilesY, scanOffset;
    private static int overlaySent, overlayReceived;
    private static int captured, sent, bytes, receivedPoses, receivedAvatars;
    private static double tokens, packetTokens;
    private static boolean sharing, spatialPresent, unsupportedReported;

    @Override
    public void onInitializeClient() {
        spatialPresent = FabricLoader.getInstance().isModLoaded("spatial_gui");
        ClientPlayNetworking.registerGlobalReceiver(
                ShareWire.CHANNEL, (client, handler, buf, sender) -> receive(buf));
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> client.execute(ShareClient::reset));
        WorldRenderEvents.LAST.register(
                context -> {
                    if (ExtrasClient.enabled()
                            && !REMOTES.isEmpty()
                            && mcaxolotl.spatialguiextras.IntegrationHooks.worldView())
                        ShareRenderer.render(context.matrixStack());
                });
        System.out.println(
                "[Spatial GUI Extras share] protocol v3, native-resolution target <= "
                        + ShareConfig.INSTANCE.maxWidth
                        + "px");
    }

    public static long streamGeneration() {
        return generation;
    }

    public static void configurationChanged() {
        if (sharing) stopSharing();
        else newSession();
    }

    public static void configurationApplied() {
        lastPose = 0;
        if (!ExtrasClient.enabled() || !ShareConfig.INSTANCE.receiveGui) {
            for (RemoteGui gui : REMOTES.values()) gui.dispose();
            REMOTES.clear();
            INCOMING.clear();
            POSES.clear();
            AVATARS.clear();
            CARRIED.clear();
            CLEARS.clear();
        } else
            for (RemoteGui gui : REMOTES.values()) {
                gui.texture().filter(ShareConfig.INSTANCE.linearFiltering);
                gui.overlayTexture().filter(ShareConfig.INSTANCE.linearFiltering);
            }
    }

    private static void newSession() {
        synchronized (READY) {
            generation++;
            READY.clear();
        }
        ShareCapture.invalidate();
        FLOW.reset();
        CONTROLS.clear();
        hashes = null;
        lastPose = 0;
    }

    private static void reset() {
        newSession();
        sharing = false;
        INCOMING.clear();
        POSES.clear();
        AVATARS.clear();
        CARRIED.clear();
        ACKS.clear();
        CONTROLS.clear();
        SESSIONS.clear();
        CLEARS.clear();
        ownId = null;
        lastPublishedSession = 0;
        for (RemoteGui gui : REMOTES.values()) gui.dispose();
        REMOTES.clear();
        SpatialPoseProvider.beginFrame();
        ShareCapture.close();
        OverflowOverlay.close();
        unsupportedReported = false;
    }

    public static void afterFrame() {
        if (!ExtrasClient.enabled()) {
            if (sharing) stopSharing();
            return;
        }
        var player = class_310.method_1551().field_1724;
        ownId = player == null ? null : player.method_5667();
        drainIncoming();
        purge();
        stats();
        if (!ShareConfig.INSTANCE.enabled) return;
        boolean capable = ClientPlayNetworking.canSend(ShareWire.CHANNEL);
        if (!spatialPresent || !capable || !SpatialHolder.available()) {
            if (sharing) stopSharing();
            if (spatialPresent
                    && !capable
                    && class_310.method_1551().field_1724 != null
                    && !unsupportedReported) {
                unsupportedReported = true;
                System.out.println(
                        "[Spatial GUI Extras share] Server has no protocol v3 relay; publishing waits for a matching Spatial GUI Extras version");
            }
            return;
        }
        if (!recipientsNearby()) {
            if (sharing) stopSharing();
            return;
        }
        try {
            if (!sharing) {
                newSession();
                sharing = true;
            }
            long now = System.nanoTime();
            if (FLOW.expire(now, ShareClient::reachable)) {
                // Credit recovery is not a new GUI. Keep the receiver's texture/animation alive.
                if (hashes != null) Arrays.fill(hashes, 0);
                lastKeyframe = 0;
            }
            if (!SpatialHolder.closing()
                    && now - lastCapture >= ShareConfig.INSTANCE.captureIntervalNanos()
                    && QUEUED.get() <= 96) {
                lastCapture = now;
                captureAndSend();
            }
            if (now - lastPose >= ShareConfig.INSTANCE.poseIntervalMs * 1_000_000L) {
                lastPose = now;
                sendPose();
                var item = OverflowOverlay.publishedVisual();
                byte[] payload = item == null ? new byte[0] : item.encode();
                int cost = payload.length + 48;
                if (item == null || (tokens >= cost && FLOW.canSend(cost))) {
                    var overlay = message(ShareWire.OVERLAY, generation);
                    overlay.method_10813(payload);
                    if (item != null) {
                        overlaySent++;
                        tokens -= cost;
                        FLOW.sent(cost, now);
                    }
                    ClientPlayNetworking.send(ShareWire.CHANNEL, overlay);
                }
            }
            flushReady();
        } catch (RuntimeException | LinkageError e) {
            if (System.nanoTime() - lastWarning > 1_000_000_000L) {
                lastWarning = System.nanoTime();
                System.err.println("[Spatial GUI Extras share] Frame failed: " + e);
            }
            stopSharing();
        }
    }

    private static boolean reachable(UUID id) {
        var client = class_310.method_1551();
        if (client.field_1687 == null || client.field_1724 == null) return false;
        double radius = Math.min(ShareConfig.INSTANCE.viewDistance, ShareWire.RELAY_RADIUS);
        for (class_742 player : client.field_1687.method_18456())
            if (player != client.field_1724
                    && player.method_5667().equals(id)
                    && player.method_19538().method_1025(client.field_1724.method_19538())
                            <= radius * radius) return true;
        return false;
    }

    static boolean recipientsNearby() {
        var client = class_310.method_1551();
        if (client.field_1687 == null) return false;
        for (class_742 player : client.field_1687.method_18456())
            if (reachable(player.method_5667())) return true;
        return false;
    }

    private static void stopSharing() {
        long closing = lastPublishedSession;
        sharing = false;
        if (closing > 0 && ClientPlayNetworking.canSend(ShareWire.CHANNEL)) {
            class_2540 buf = message(ShareWire.CLEAR, closing);
            ClientPlayNetworking.send(ShareWire.CHANNEL, buf);
        }
        lastPublishedSession = 0;
        newSession();
    }

    private static class_2540 message(byte type, long session) {
        class_2540 buf = PacketByteBufs.create();
        buf.writeByte(type);
        buf.writeLong(session);
        return buf;
    }

    private static void sendPose() {
        var snapshot = SpatialHolder.snapshot();
        if (snapshot == null) return;
        byte[] data = encodePose(snapshot);
        ShareProtocol.validatePose(data);
        class_2540 buf = message(ShareWire.POSE, generation);
        buf.method_10813(data);
        ClientPlayNetworking.send(ShareWire.CHANNEL, buf);
        lastPublishedSession = generation;
        var renderer = org.tastytrash.spatialGUI.client.SpatialGUIClient.renderer();
        boolean firstPerson =
                renderer.isClosing()
                        ? renderer.isClosingFirstPerson()
                        : org.tastytrash.spatialGUI.client.SpatialGUIClient
                                .getEffectiveFirstPersonMode();
        AvatarPose avatar = AvatarDisplay.outgoing(firstPerson);
        class_2540 display = message(ShareWire.AVATAR, generation);
        display.method_10813(AvatarPose.encode(avatar));
        ClientPlayNetworking.send(ShareWire.CHANNEL, display);
    }

    private static byte[] encodePose(SpatialPoseProvider.Snapshot s) {
        ByteBuffer b = ByteBuffer.allocate(ShareProtocol.POSE_BYTES);
        b.put((byte) ((s.curved() ? 1 : 0) | (s.closing() ? 2 : 0)));
        for (float v :
                new float[] {
                    s.centerX(),
                    s.centerY(),
                    s.centerZ(),
                    s.rightX(),
                    s.rightY(),
                    s.rightZ(),
                    s.upX(),
                    s.upY(),
                    s.upZ(),
                    s.halfWidth(),
                    s.halfHeight(),
                    s.arc(),
                    s.radius(),
                    s.cylinderHalfHeight(),
                    s.axisX(),
                    s.axisY(),
                    s.axisZ(),
                    s.axisDirX(),
                    s.axisDirY(),
                    s.axisDirZ(),
                    s.refDirX(),
                    s.refDirY(),
                    s.refDirZ(),
                    s.opacity()
                }) b.putFloat(v);
        b.putInt(s.fadeMs());
        return b.array();
    }

    private static RemoteGui.Pose decodePose(byte[] payload) {
        ByteBuffer b = ByteBuffer.wrap(payload);
        boolean curved = (b.get() & 1) != 0;
        return new RemoteGui.Pose(
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                curved,
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat());
    }

    private static void applyPose(UUID sender, ShareProtocol.Message update) {
        RemoteGui gui = remote(sender);
        if (!gui.session(update.session())) return;
        gui.activate();
        gui.pose(decodePose(update.data()));
        ByteBuffer b = ByteBuffer.wrap(update.data());
        gui.animation(b.getFloat(93), b.getInt(97));
        receivedPoses++;
    }

    private static void captureAndSend() {
        int[] size = SpatialHolder.targetSize();
        int fbo = SpatialHolder.framebuffer();
        if (fbo <= 0 || size == null || size[0] <= 0 || size[1] <= 0) return;
        double factor =
                Math.min(
                        1,
                        Math.min(
                                ShareConfig.INSTANCE.maxWidth / (double) size[0],
                                ShareWire.MAX_DIMENSION / (double) size[1]));
        int w = Math.max(1, (int) Math.round(size[0] * factor)),
                h = Math.max(1, (int) Math.round(size[1] * factor));
        if (hashes == null || w != width || h != height) {
            newSession();
            width = w;
            height = h;
            scanOffset = 0;
            tilesX = (w + ShareConfig.INSTANCE.tileSize - 1) / ShareConfig.INSTANCE.tileSize;
            tilesY = (h + ShareConfig.INSTANCE.tileSize - 1) / ShareConfig.INSTANCE.tileSize;
            hashes = new int[tilesX * tilesY];
            System.out.println(
                    "[Spatial GUI Extras share] capture "
                            + size[0]
                            + "x"
                            + size[1]
                            + " -> "
                            + w
                            + "x"
                            + h);
        }
        ByteBuffer pixels = ShareCapture.captureAsync(fbo, size[0], size[1], width, height);
        if (pixels == null) return;
        SharedCursor.paint(pixels, width, height);
        captured++;
        long now = System.nanoTime();
        if (now - lastKeyframe > ShareConfig.INSTANCE.keyframeIntervalMs * 1_000_000L) {
            Arrays.fill(hashes, 0);
            lastKeyframe = now;
        }
        int budget = ShareConfig.INSTANCE.maxTilesPerFrame, total = tilesX * tilesY, step = 0;
        long frame = frameId++;
        for (; step < total && budget > 0; step++) {
            int index = (scanOffset + step) % total,
                    x = (index % tilesX) * ShareConfig.INSTANCE.tileSize,
                    y = (index / tilesX) * ShareConfig.INSTANCE.tileSize;
            int tw = Math.min(ShareConfig.INSTANCE.tileSize, width - x),
                    th = Math.min(ShareConfig.INSTANCE.tileSize, height - y);
            int hash = hashTile(pixels, width, x, y, tw, th);
            if (hash == hashes[index]) continue;
            byte[] data = copyTile(pixels, width, x, y, tw, th);
            if (enqueueTile(frame, width, height, x, y, tw, th, data)) hashes[index] = hash;
            budget--;
        }
        scanOffset = (scanOffset + Math.max(1, step)) % total;
    }

    private static boolean enqueueTile(
            long frame, int w, int h, int x, int y, int tw, int th, byte[] data) {
        if (QUEUED.get() >= 128) return false;
        long session = generation;
        QUEUED.incrementAndGet();
        COMPRESSOR.execute(
                () -> {
                    try {
                        byte[] encoded = deflate(data);
                        synchronized (READY) {
                            if (session == generation)
                                READY.offer(
                                        y * ShareWire.MAX_DIMENSION + x,
                                        new Tile(session, frame, w, h, x, y, tw, th, encoded));
                        }
                    } finally {
                        QUEUED.decrementAndGet();
                    }
                });
        return true;
    }

    private static double refillTokens(double balance, double rate, double seconds) {
        return Math.min(
                Math.max(rate / 10, ShareWire.MAX_PAYLOAD_BYTES),
                balance + Math.max(0, seconds) * rate);
    }

    private static void flushReady() {
        long now = System.nanoTime();
        double rate = ShareConfig.INSTANCE.bandwidthBudgetKiBps * 1024.0;
        if (bandwidthTime == 0) bandwidthTime = now;
        tokens = refillTokens(tokens, rate, (now - bandwidthTime) / 1e9);
        packetTokens = Math.min(128, packetTokens + (now - bandwidthTime) / 1e9 * 6000);
        bandwidthTime = now;
        for (int burst = 0; burst < 32; burst++) {
            var entry = READY.first();
            if (entry == null) break;
            Tile t = entry.getValue();
            int cost = t.data.length + 48;
            if (tokens < cost || packetTokens < 1 || !FLOW.canSend(cost)) break;
            if (!READY.remove(entry.getKey(), t)) continue;
            if (t.session != generation) continue;
            class_2540 buf = message(ShareWire.TILE, t.session);
            buf.writeLong(t.frame);
            for (int n : new int[] {t.width, t.height, t.x, t.y, t.tw, t.th}) buf.writeInt(n);
            buf.method_10813(t.data);
            FLOW.sent(cost, now);
            tokens -= cost;
            packetTokens--;
            sent++;
            bytes += cost;
            ClientPlayNetworking.send(ShareWire.CHANNEL, buf);
        }
    }

    private static void receive(class_2540 buf) {
        if (!ExtrasClient.enabled()) return;
        try {
            if (buf.readableBytes() < 25 || buf.readableBytes() > ShareWire.MAX_PAYLOAD_BYTES + 16)
                throw new IllegalArgumentException("relay size");
            UUID sender = buf.method_10790();
            ShareProtocol.Message m = ShareProtocol.read(buf);
            long now = System.nanoTime();
            if (m.type() == ShareWire.ACK) {
                UUID self = ownId;
                if (self != null && self.equals(m.owner()) && m.session() == generation)
                    CONTROLS.add(sender, m.session(), m.bytes());
            } else if (m.type() == ShareWire.CLEAR) {
                if (!SESSIONS.close(sender, m.session(), now)) return;
                ACKS.remove(sender);
                AVATARS.remove(sender);
                CARRIED.remove(sender);
                POSES.computeIfPresent(
                        sender, (id, old) -> old.session() <= m.session() ? null : old);
                if (ShareConfig.INSTANCE.receiveGui) CLEARS.offer(sender, new Update(sender, m));
            } else if (m.type() == ShareWire.POSE) {
                if (!SESSIONS.open(sender, m.session(), now)) return;
                ACKS.discardBefore(sender, m.session());
                if (ShareConfig.INSTANCE.receiveGui)
                    POSES.compute(
                            sender,
                            (id, old) -> old == null || old.session() <= m.session() ? m : old);
            } else if (m.type() == ShareWire.OVERLAY) {
                if (!SESSIONS.tile(sender, m.session(), now)) return;
                if (m.data().length > OverlayPose.BYTES)
                    ACKS.add(sender, m.session(), m.data().length + 48);
                if (ShareConfig.INSTANCE.receiveGui)
                    CARRIED.compute(
                            sender,
                            (id, old) -> old == null || old.session() <= m.session() ? m : old);
            } else if (m.type() == ShareWire.AVATAR) {
                if (!SESSIONS.tile(sender, m.session(), now)) return;
                if (ShareConfig.INSTANCE.receiveGui && ShareConfig.INSTANCE.receiveAvatar)
                    AVATARS.compute(
                            sender,
                            (id, old) -> old == null || old.session() <= m.session() ? m : old);
            } else {
                if (!SESSIONS.tile(sender, m.session(), now)) return;
                ACKS.add(sender, m.session(), m.data().length + 48);
                if (ShareConfig.INSTANCE.receiveGui) {
                    INCOMING.offer(new TileKey(sender, m.x(), m.y()), new Update(sender, m));
                    RECEIVED.incrementAndGet();
                }
            }
        } catch (RuntimeException e) {
            INVALID.incrementAndGet();
            if (System.nanoTime() - lastWarning > 1_000_000_000L) {
                lastWarning = System.nanoTime();
                System.err.println("[Spatial GUI Extras share] Rejected incoming packet: " + e);
            }
        }
    }

    private static void drainIncoming() {
        if (ClientPlayNetworking.canSend(ShareWire.CHANNEL))
            for (var entry : ACKS.drain()) {
                class_2540 ack = message(ShareWire.ACK, entry.session());
                ack.method_10797(entry.sender());
                ack.writeInt(entry.bytes());
                ClientPlayNetworking.send(ShareWire.CHANNEL, ack);
            }
        for (var ack : CONTROLS.drain())
            if (ack.session() == generation)
                FLOW.acknowledge(
                        ack.sender(), ack.bytes(), System.nanoTime(), ShareClient::reachable);
        if (!ShareConfig.INSTANCE.receiveGui) {
            INCOMING.clear();
            POSES.clear();
            AVATARS.clear();
            CARRIED.clear();
            CLEARS.clear();
            return;
        }
        for (var entry = CLEARS.first(); entry != null; entry = CLEARS.first()) {
            Update clear = entry.getValue();
            if (!CLEARS.remove(entry.getKey(), clear)) continue;
            RemoteGui gui = remote(clear.sender);
            if (gui.session() == clear.message.session() || gui.session(clear.message.session()))
                gui.clear();
        }
        for (var entry : POSES.entrySet())
            if (POSES.remove(entry.getKey(), entry.getValue()))
                applyPose(entry.getKey(), entry.getValue());
        for (var entry : AVATARS.entrySet())
            if (AVATARS.remove(entry.getKey(), entry.getValue())) {
                var update = entry.getValue();
                RemoteGui gui = REMOTES.get(entry.getKey());
                if (gui != null && gui.session() == update.session() && gui.active()) {
                    gui.avatar(AvatarPose.decode(update.data()), System.nanoTime());
                    receivedAvatars++;
                }
            }
        for (var entry : CARRIED.entrySet())
            if (CARRIED.remove(entry.getKey(), entry.getValue())) {
                var update = entry.getValue();
                var gui = REMOTES.get(entry.getKey());
                if (gui != null && gui.active() && gui.session() == update.session()) {
                    var overlayItem = OverlayVisual.decode(update.data());
                    gui.overlayVisual(overlayItem, System.nanoTime());
                    if (overlayItem != null) overlayReceived++;
                }
            }
        for (int i = 0; i < 128; i++) {
            var entry = INCOMING.first();
            if (entry == null) break;
            if (!INCOMING.remove(entry.getKey(), entry.getValue())) continue;
            Update u = entry.getValue();
            var m = u.message;
            RemoteGui gui = remote(u.sender);
            if (!gui.session(m.session())
                    || !gui.acceptTile(m.frame(), m.width(), m.height(), m.x(), m.y())) continue;
            byte[] pixels = ShareProtocol.inflate(m.data(), m.tw() * m.th() * 4);
            if (pixels == null) continue;
            gui.touch();
            gui.uploadTile(m.width(), m.height(), m.x(), m.y(), m.tw(), m.th(), pixels);
        }
    }

    private static RemoteGui remote(UUID id) {
        return REMOTES.computeIfAbsent(id, RemoteGui::new);
    }

    public static List<RemoteGui> visibleRemotes() {
        List<RemoteGui> visible = new ArrayList<>();
        if (!ShareConfig.INSTANCE.receiveGui) return visible;
        for (RemoteGui gui : REMOTES.values())
            if (reachable(gui.sender())
                    && gui.pose() != null
                    && gui.texture().width() > 0
                    && alphaFor(gui) > 0.01f) visible.add(gui);
        var player = class_310.method_1551().field_1724;
        if (visible.size() > ShareConfig.INSTANCE.maxVisiblePanels && player != null) {
            Map<UUID, Double> distances = new HashMap<>();
            for (class_742 candidate : class_310.method_1551().field_1687.method_18456())
                distances.put(
                        candidate.method_5667(),
                        candidate.method_19538().method_1025(player.method_19538()));
            visible.sort(
                    java.util.Comparator.<RemoteGui>comparingDouble(
                                    gui ->
                                            distances.getOrDefault(
                                                    gui.sender(), Double.POSITIVE_INFINITY))
                            .thenComparing(RemoteGui::sender));
            return new ArrayList<>(visible.subList(0, ShareConfig.INSTANCE.maxVisiblePanels));
        }
        return visible;
    }

    public static AvatarPose avatar(UUID player) {
        if (!ExtrasClient.enabled()
                || !ShareConfig.INSTANCE.receiveGui
                || !ShareConfig.INSTANCE.receiveAvatar
                || !reachable(player)) return null;
        RemoteGui gui = REMOTES.get(player);
        return gui == null ? null : gui.avatar(System.nanoTime());
    }

    public static float avatarWeight(UUID player) {
        RemoteGui gui = REMOTES.get(player);
        return gui == null ? 0 : gui.avatarWeight(System.nanoTime());
    }

    static float alphaFor(RemoteGui gui) {
        return alphaFor(gui, System.nanoTime());
    }

    private static float alphaFor(RemoteGui gui, long now) {
        float alpha = gui.opacity() * ShareConfig.INSTANCE.panelOpacity;
        if (!gui.active())
            return gui.fadeMs() == 0
                    ? 0
                    : alpha
                            * (float)
                                    Math.max(
                                            0,
                                            1 - (now - gui.closedNanos()) / (gui.fadeMs() * 1e6));
        double idle = (now - gui.lastPacketNanos()) / 1e6;
        return idle <= 1500 ? alpha : alpha * (float) Math.max(0, 1 - (idle - 1500) / 700);
    }

    private static void purge() {
        long now = System.nanoTime();
        if (now - lastPrune > 1_000_000_000L) {
            for (UUID id : SESSIONS.expire(now)) {
                ACKS.remove(id);
                POSES.remove(id);
                AVATARS.remove(id);
                CARRIED.remove(id);
                INCOMING.removeIf(key -> key.sender.equals(id));
            }
            lastPrune = now;
        }
        var it = REMOTES.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getValue().millisSincePacket() > 5000) {
                e.getValue().dispose();
                it.remove();
            }
        }
    }

    private static void stats() {
        long now = System.nanoTime();
        if (statsTime == 0) statsTime = now;
        if (now - statsTime < 1_000_000_000L) return;
        int received = RECEIVED.getAndSet(0), invalid = INVALID.getAndSet(0);
        if (ShareConfig.INSTANCE.statisticsLogging && (sharing || received > 0 || invalid > 0))
            System.out.println(
                    "[Spatial GUI Extras share] captures="
                            + captured
                            + " tilesSent="
                            + sent
                            + " sentKiB="
                            + bytes / 1024
                            + " tilesReceived="
                            + received
                            + " posesReceived="
                            + receivedPoses
                            + " overlaySent="
                            + overlaySent
                            + " overlayReceived="
                            + overlayReceived
                            + " avatarUpdates="
                            + receivedAvatars
                            + " invalid="
                            + invalid
                            + " inFlightKiB="
                            + FLOW.outstanding() / 1024
                            + " ready="
                            + READY.size()
                            + " incoming="
                            + INCOMING.size());
        statsTime = now;
        overlaySent = overlayReceived = 0;
        captured = sent = bytes = receivedPoses = receivedAvatars = 0;
    }

    private static int hashTile(
            ByteBuffer source, int stride, int x, int y, int width, int height) {
        CRC.reset();
        int rowBytes = width * 4;
        ByteBuffer row = source.duplicate();
        for (int index = 0; index < height; index++) {
            int start = ((y + index) * stride + x) * 4;
            // Widen first, then move: a narrowed limit would reject the next row.
            row.limit(start + rowBytes);
            row.position(start);
            CRC.update(row);
        }
        return (int) CRC.getValue();
    }

    private static byte[] copyTile(
            ByteBuffer source, int stride, int x, int y, int width, int height) {
        byte[] tile = new byte[width * height * 4];
        int rowBytes = width * 4;
        ByteBuffer row = source.duplicate();
        for (int index = 0; index < height; index++) {
            int start = ((y + index) * stride + x) * 4;
            row.limit(start + rowBytes);
            row.position(start);
            row.get(tile, index * rowBytes, rowBytes);
        }
        return tile;
    }

    private static final ThreadLocal<Deflater> DEFLATERS =
            ThreadLocal.withInitial(() -> new Deflater(Deflater.BEST_SPEED));
    private static final ThreadLocal<Inflater> INFLATERS = ThreadLocal.withInitial(Inflater::new);

    private static byte[] deflate(byte[] input) {
        Deflater deflater = DEFLATERS.get();
        deflater.reset();
        deflater.setInput(input);
        deflater.finish();
        byte[] output = new byte[input.length];
        int size = deflater.deflate(output);
        if (!deflater.finished() || size <= 0 || size >= input.length) return input;
        byte[] trimmed = new byte[size];
        System.arraycopy(output, 0, trimmed, 0, size);
        return trimmed;
    }

    private static byte[] inflate(byte[] input, int expected) {
        if (input.length == expected) return input;
        Inflater inflater = INFLATERS.get();
        inflater.reset();
        try {
            inflater.setInput(input);
            byte[] output = new byte[expected];
            int offset = 0;
            while (!inflater.finished() && offset < expected) {
                int size = inflater.inflate(output, offset, expected - offset);
                if (size == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break;
                }
                offset += size;
            }
            if (offset != expected || !inflater.finished() || inflater.getRemaining() != 0)
                return null;
            return output;
        } catch (java.util.zip.DataFormatException e) {
            return null;
        }
    }

    private static final class SpatialHolder {
        static boolean available() {
            return SpatialPoseProvider.available();
        }

        static boolean closing() {
            return org.tastytrash.spatialGUI.client.SpatialGUIClient.renderer().isClosing();
        }

        static SpatialPoseProvider.Snapshot snapshot() {
            return SpatialPoseProvider.snapshot();
        }

        static int framebuffer() {
            var t =
                    org.tastytrash
                            .spatialGUI
                            .client
                            .SpatialGUIClient
                            .renderer()
                            .getTargetManager()
                            .getInventoryTarget();
            return t == null ? -1 : t.field_1476;
        }

        static int[] targetSize() {
            var t =
                    org.tastytrash
                            .spatialGUI
                            .client
                            .SpatialGUIClient
                            .renderer()
                            .getTargetManager()
                            .getInventoryTarget();
            return t == null ? null : new int[] {t.field_1480, t.field_1477};
        }
    }
}
