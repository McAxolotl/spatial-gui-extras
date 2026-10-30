package mcaxolotl.spatialguiextras;

import io.netty.buffer.Unpooled;

import mcaxolotl.spatialguiextras.share.*;

import net.minecraft.class_2540;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class SecurityTests {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static class_2540 packet(UUID owner, byte type, long session) {
        class_2540 buffer = new class_2540(Unpooled.buffer());
        buffer.method_10797(owner);
        buffer.writeByte(type);
        buffer.writeLong(session);
        return buffer;
    }

    private static void receive(Method receiver, class_2540 packet) throws Exception {
        try {
            receiver.invoke(null, packet);
        } finally {
            packet.release();
        }
    }

    public static void main(String[] args) throws Exception {
        UUID owner = UUID.randomUUID(), viewer = UUID.randomUUID();
        long now = 1;
        RelayRoutes routes = new RelayRoutes();
        for (int session = 1; session <= 10000; session++) {
            check(!routes.tile(owner, session, now), "unannounced tile accepted");
            check(!routes.reserve(owner, viewer, session, 100), "unannounced route allocated");
            check(routes.open(owner, session, now), "new announced session rejected");
            check(routes.tile(owner, session, now), "announced tile rejected");
            check(routes.reserve(owner, viewer, session, 100), "valid route rejected");
            check(
                    routes.routeCount() == 1 && routes.senderCount() == 1,
                    "server retained old-session routes");
            now++;
        }
        check(
                routes.acknowledge(owner, viewer, 10000, 100) && routes.routeCount() == 0,
                "settled route retained");
        check(routes.close(owner, 10000, now), "close rejected");
        check(
                !routes.open(owner, 10000, now) && !routes.tile(owner, 10000, now),
                "closed stream resurrected");
        check(routes.open(owner, 10001, now), "next stream blocked");
        routes.reserve(owner, viewer, 10001, 100);
        routes.retainViewers(owner, Set.of());
        check(routes.routeCount() == 0, "departed viewer retained credit route");
        routes.reserve(owner, viewer, 10001, 100);
        routes.expire(now + StreamSessions.IDLE_NANOS + 1);
        check(routes.routeCount() == 0 && routes.senderCount() == 0, "idle route retained");
        StreamSessions sessions = new StreamSessions();
        for (int i = 0; i < StreamSessions.MAX_SENDERS + 100; i++)
            sessions.open(new UUID(0, i), 1, 1);
        check(sessions.size() == StreamSessions.MAX_SENDERS, "sender keyspace unbounded");
        AckAccumulator accumulator = new AckAccumulator();
        for (int session = 1; session <= 10000; session++) accumulator.add(owner, session, 10);
        check(accumulator.size() == 1, "ACKs retained per-session keys");
        check(
                accumulator.drain().get(0).session() == 10000 && accumulator.size() == 0,
                "ACK drain kept settled keys");
        AtomicInteger total = new AtomicInteger();
        Thread writer =
                new Thread(
                        () -> {
                            for (int i = 0; i < 10000; i++) accumulator.add(owner, 10001, 1);
                        });
        writer.start();
        while (writer.isAlive()) for (var ack : accumulator.drain()) total.addAndGet(ack.bytes());
        writer.join();
        for (var ack : accumulator.drain()) total.addAndGet(ack.bytes());
        check(total.get() == 10000 && accumulator.size() == 0, "concurrent ACK drain lost updates");
        FlowWindow window = new FlowWindow();
        UUID outside = UUID.randomUUID();
        for (int i = 0; i < 20; i++) {
            window.sent(100, i * 100L + 1);
            window.acknowledge(outside, 100, i * 100L + 2, id -> id.equals(viewer));
            check(
                    !window.expire(i * 100L + 3, id -> id.equals(viewer)),
                    "out-of-range ACK selected a reset-loop peer");
            window.acknowledge(viewer, 100, i * 100L + 4, id -> id.equals(viewer));
            check(window.outstanding() == 0, "eligible viewer cannot return credits");
        }
        Method receiver = ShareClient.class.getDeclaredMethod("receive", class_2540.class);
        receiver.setAccessible(true);
        Field field = ShareClient.class.getDeclaredField("ACKS");
        field.setAccessible(true);
        AckAccumulator realAcks = (AckAccumulator) field.get(null);
        for (int session = 1; session <= 10000; session++) {
            class_2540 tile = packet(owner, ShareWire.TILE, session);
            tile.writeLong(1);
            for (int n : new int[] {1, 1, 0, 0, 1, 1}) tile.writeInt(n);
            tile.method_10813(new byte[4]);
            receive(receiver, tile);
        }
        check(
                realAcks.size() == 0,
                "production receive allocated ACKs without an announced stream");
        for (int session = 1; session <= 10000; session++) {
            class_2540 pose = packet(owner, ShareWire.POSE, session);
            pose.method_10813(CoreTests.pose());
            receive(receiver, pose);
            class_2540 tile = packet(owner, ShareWire.TILE, session);
            tile.writeLong(1);
            for (int n : new int[] {1, 1, 0, 0, 1, 1}) tile.writeInt(n);
            tile.method_10813(new byte[4]);
            receive(receiver, tile);
            if (session % 1000 == 0)
                check(
                        realAcks.size() == 1,
                        "production ACK cardinality grew with announced sessions");
        }
        check(
                realAcks.drain().get(0).session() == 10000 && realAcks.size() == 0,
                "production ACK cleanup failed");
        class_2540 clear = packet(owner, ShareWire.CLEAR, 10000);
        receive(receiver, clear);
        Field ledgerField = ShareClient.class.getDeclaredField("SESSIONS");
        ledgerField.setAccessible(true);
        check(
                !((StreamSessions) ledgerField.get(null)).active(owner, 10000),
                "closed receive session remains active");
        AvatarPose avatar = AvatarPose.displayed(179, 30, 15);
        check(avatar.equals(AvatarPose.decode(AvatarPose.encode(avatar))), "avatar wire roundtrip");
        check(AvatarPose.decode(AvatarPose.encode(null)) == null, "inactive avatar roundtrip");
        AvatarPose blended = avatar.blend(AvatarPose.displayed(-179, 40, -15), .5f);
        check(
                Math.abs(Math.abs(blended.body()) - 180) < .001
                        && blended.head() == 35
                        && blended.pitch() == 0,
                "avatar interpolation took long rotation path");
        class_2540 avatarPacket = packet(owner, ShareWire.AVATAR, 10000);
        avatarPacket.method_10813(AvatarPose.encode(avatar));
        receive(receiver, avatarPacket);
        Field pendingAvatars = ShareClient.class.getDeclaredField("AVATARS");
        pendingAvatars.setAccessible(true);
        check(
                ((Map<?, ?>) pendingAvatars.get(null)).isEmpty(),
                "closed session accepted avatar payload");
        class_2540 validAvatar = CoreTests.buffer();
        validAvatar.writeByte(ShareWire.AVATAR);
        validAvatar.writeLong(1);
        validAvatar.method_10813(AvatarPose.encode(avatar));
        check(
                ShareProtocol.read(validAvatar).type() == ShareWire.AVATAR,
                "avatar message rejected");
        validAvatar.release();
        byte[] invalidAvatar = AvatarPose.encode(avatar);
        java.nio.ByteBuffer.wrap(invalidAvatar).putFloat(1, Float.NaN);
        try {
            AvatarPose.decode(invalidAvatar);
            throw new AssertionError("nonfinite avatar accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        Field incoming = ShareClient.class.getDeclaredField("INCOMING");
        incoming.setAccessible(true);
        int queuedBefore = ((LatestQueue<?, ?>) incoming.get(null)).size();
        UUID muted = UUID.randomUUID();
        ShareConfig.INSTANCE.receiveGui = false;
        class_2540 mutedPose = packet(muted, ShareWire.POSE, 1);
        mutedPose.method_10813(CoreTests.pose());
        receive(receiver, mutedPose);
        class_2540 mutedTile = packet(muted, ShareWire.TILE, 1);
        mutedTile.writeLong(1);
        for (int n : new int[] {1, 1, 0, 0, 1, 1}) mutedTile.writeInt(n);
        mutedTile.method_10813(new byte[4]);
        receive(receiver, mutedTile);
        check(realAcks.size() == 1, "disabled reception blocked flow credits");
        check(
                ((LatestQueue<?, ?>) incoming.get(null)).size() == queuedBefore,
                "disabled reception queued image data");
        Field poses = ShareClient.class.getDeclaredField("POSES");
        poses.setAccessible(true);
        check(
                !((Map<?, ?>) poses.get(null)).containsKey(muted),
                "disabled reception queued a drawable pose");
        ShareConfig.INSTANCE.receiveGui = true;
        realAcks.clear();
        System.out.println(
                "PASS security checks="
                        + checks
                        + " (10k-session route/ACK attacks, real receiver floods, atomic drain, peer range, expiry)");
    }
}
