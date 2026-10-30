package mcaxolotl.spatialguiextras.share;

import java.util.UUID;
import java.util.function.Predicate;

public final class FlowWindow {
    public static final int LIMIT = 256 * 1024;
    public static final long TIMEOUT = 1_000_000_000L;
    private UUID peer;
    private int outstanding;
    private long deadline;

    public synchronized boolean canSend(int bytes) {
        return bytes > 0 && outstanding <= LIMIT - bytes;
    }

    public synchronized void sent(int bytes, long now) {
        if (outstanding == 0) deadline = now + TIMEOUT;
        outstanding += bytes;
    }

    public synchronized void acknowledge(
            UUID sender, int bytes, long now, Predicate<UUID> eligible) {
        if (bytes <= 0 || bytes > LIMIT || !eligible.test(sender)) return;
        if (peer == null) peer = sender;
        if (peer.equals(sender)) {
            outstanding = Math.max(0, outstanding - bytes);
            deadline = now + TIMEOUT;
        }
    }

    public synchronized boolean expire(long now, Predicate<UUID> reachable) {
        if ((peer != null && !reachable.test(peer)) || (outstanding > 0 && now >= deadline)) {
            reset();
            return true;
        }
        return false;
    }

    public synchronized void reset() {
        peer = null;
        outstanding = 0;
        deadline = 0;
    }

    public synchronized int outstanding() {
        return outstanding;
    }
}
