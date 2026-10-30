package mcaxolotl.spatialguiextras;

import io.netty.buffer.Unpooled;

import mcaxolotl.spatialguiextras.share.*;

import net.minecraft.class_2540;
import net.minecraft.class_2817;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.*;

public final class CoreTests {
    private static int checks;

    public static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    static class_2540 buffer() {
        return new class_2540(Unpooled.buffer());
    }

    static byte[] pose() {
        ByteBuffer b = ByteBuffer.allocate(ShareProtocol.POSE_BYTES);
        b.put((byte) 1);
        for (float v :
                new float[] {
                    2, 3, 4, 1, 0, 0, 0, 1, 0, 2, 1, .5f, 8, 1, 5, 6, 7, 0, 1, 0, 0, 0, -1, .75f
                }) b.putFloat(v);
        b.putInt(300);
        return b.array();
    }

    static void reject(class_2540 buf) {
        try {
            ShareProtocol.read(buf);
            throw new AssertionError("invalid message accepted");
        } catch (RuntimeException expected) {
            checks++;
        } finally {
            buf.release();
        }
    }

    public static void main(String[] args) throws Exception {
        LatestQueue<Integer, Integer> q = new LatestQueue<>(64);
        for (int i = 0; i < 64; i++) q.offer(i, 0);
        Set<Integer> seen = new HashSet<>();
        for (int round = 0; round < 20; round++) {
            for (int i = 0; i < 32; i++) q.offer(i, round);
            for (int i = 0; i < 32; i++) {
                var e = q.first();
                if (e == null) break;
                seen.add(e.getKey());
                q.remove(e.getKey(), e.getValue());
            }
        }
        check(seen.size() == 64, "continuous updates starved queued tiles");
        Method tokenRefill =
                ShareClient.class.getDeclaredMethod(
                        "refillTokens", double.class, double.class, double.class);
        tokenRefill.setAccessible(true);
        double lowBudget = (double) tokenRefill.invoke(null, 0d, 128 * 1024d, 1d);
        check(
                lowBudget >= OverlayVisual.MAX_BYTES + 48,
                "minimum upload budget can never send one complete overlay");
        LatestQueue<Integer, String> latest = new LatestQueue<>(1);
        latest.offer(1, "old");
        latest.offer(1, "new");
        check(
                !latest.offer(2, "overflow") && latest.first().getValue().equals("new"),
                "queue bound/latest value");
        FlowWindow flow = new FlowWindow();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        flow.sent(100, 1);
        flow.acknowledge(a, 50, 2, id -> true);
        check(flow.expire(3, id -> !id.equals(a)), "unreachable ACK peer retained");
        flow.sent(100, 4);
        flow.acknowledge(b, 100, 5, id -> true);
        check(flow.outstanding() == 0, "replacement viewer cannot ACK");
        flow.sent(FlowWindow.LIMIT, 10);
        check(!flow.canSend(1), "window not bounded");
        check(
                flow.expire(FlowWindow.TIMEOUT + 11, id -> true) && flow.canSend(1),
                "ACK deadline did not recover");
        RelayBudget budget = new RelayBudget();
        int accepted = 0;
        while (budget.accept(16384, 1)) accepted++;
        check(accepted <= 32 && accepted > 0, "relay budget not enforced");
        check(budget.accept(16384, 1_000_000_001L), "relay does not refill");
        byte[] pose = pose();
        ShareProtocol.validatePose(pose);
        checks++;
        class_2540 p = buffer();
        p.writeByte(ShareWire.POSE);
        p.writeLong(1);
        p.method_10813(pose);
        check(
                ShareProtocol.read(p).data().length == ShareProtocol.POSE_BYTES,
                "valid pose rejected");
        p.release();
        p = buffer();
        p.writeByte(ShareWire.POSE);
        p.writeLong(1);
        p.method_10813(new byte[129]);
        reject(p);
        byte[] nan = pose.clone();
        ByteBuffer.wrap(nan).putFloat(1, Float.NaN);
        p = buffer();
        p.writeByte(ShareWire.POSE);
        p.writeLong(1);
        p.method_10813(nan);
        reject(p);
        p = buffer();
        p.writeByte(ShareWire.CLEAR);
        p.writeLong(1);
        p.writeByte(9);
        reject(p);
        p = buffer();
        p.writeByte(ShareWire.TILE);
        p.writeLong(1);
        p.writeLong(1);
        for (int n : new int[] {64, 64, Integer.MAX_VALUE, 0, 64, 64}) p.writeInt(n);
        p.method_10813(new byte[16384]);
        reject(p);
        Properties config = new Properties();
        config.setProperty("shareTileSize", "128");
        ShareConfig.INSTANCE.load(config);
        check(ShareConfig.INSTANCE.tileSize == 64, "unsafe raw tile size permitted");
        Random random = new Random(762894);
        byte[] data = new byte[16384];
        random.nextBytes(data);
        p = buffer();
        p.method_10812(ShareWire.CHANNEL);
        p.writeByte(ShareWire.TILE);
        p.writeLong(1);
        p.writeLong(1);
        for (int n : new int[] {64, 64, 0, 0, 64, 64}) p.writeInt(n);
        p.method_10813(data);
        new class_2817(p);
        checks++;
        p.release();
        config.setProperty("sharePanelScale", "NaN");
        try {
            ShareConfig.INSTANCE.load(config);
            throw new AssertionError("NaN config accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        config.remove("sharePanelScale");
        ShareConfig.INSTANCE.load(config);
        Method copy =
                ShareClient.class.getDeclaredMethod(
                        "copyTile",
                        ByteBuffer.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class);
        Method hash =
                ShareClient.class.getDeclaredMethod(
                        "hashTile",
                        ByteBuffer.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class);
        Method encode = ShareClient.class.getDeclaredMethod("deflate", byte[].class);
        for (Method m : new Method[] {copy, hash, encode}) m.setAccessible(true);
        for (int[] dims : new int[][] {{960, 1011}, {1920, 1080}, {2048, 1537}, {257, 129}}) {
            int w = dims[0], h = dims[1];
            byte[] bytes = new byte[w * h * 4];
            random.nextBytes(bytes);
            Arrays.fill(bytes, 0, bytes.length / 3, (byte) 0);
            ByteBuffer source = ByteBuffer.allocateDirect(bytes.length);
            source.put(bytes).flip();
            for (int y = 0; y < h; y += 64)
                for (int x = 0; x < w; x += 64) {
                    int tw = Math.min(64, w - x), th = Math.min(64, h - y);
                    byte[] expected = new byte[tw * th * 4];
                    for (int row = 0; row < th; row++)
                        System.arraycopy(
                                bytes, ((y + row) * w + x) * 4, expected, row * tw * 4, tw * 4);
                    byte[] got = (byte[]) copy.invoke(null, source, w, x, y, tw, th);
                    check(Arrays.equals(expected, got), "tile pixels");
                    CRC32 crc = new CRC32();
                    crc.update(expected);
                    check(
                            (int) hash.invoke(null, source, w, x, y, tw, th)
                                    == (int) crc.getValue(),
                            "tile CRC");
                    byte[] compressed = (byte[]) encode.invoke(null, (Object) got);
                    check(
                            Arrays.equals(
                                    expected, ShareProtocol.inflate(compressed, expected.length)),
                            "codec");
                    if (compressed.length < got.length)
                        check(
                                ShareProtocol.inflate(
                                                Arrays.copyOf(compressed, compressed.length - 1),
                                                got.length)
                                        == null,
                                "truncated codec");
                }
        }
        Method receive = ShareClient.class.getDeclaredMethod("receive", class_2540.class);
        receive.setAccessible(true);
        p = buffer();
        p.method_10797(a);
        p.writeByte(ShareWire.POSE);
        p.writeLong(1);
        p.method_10813(new byte[129]);
        receive.invoke(null, p);
        checks++;
        p.release();
        System.out.println(
                "PASS core checks="
                        + checks
                        + " (fair scheduling, ACK recovery, relay bounds, protocol, pixel/CRC/codec, malformed receive)");
    }
}
