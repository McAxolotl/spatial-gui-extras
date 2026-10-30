package mcaxolotl.spatialguiextras.share;

import java.util.*;

public final class AckAccumulator {
    public record Ack(UUID sender, long session, int bytes) {}

    private final LinkedHashMap<UUID, Ack> pending = new LinkedHashMap<>();

    public synchronized boolean add(UUID sender, long session, int bytes) {
        if (session <= 0 || bytes <= 0 || bytes > FlowWindow.LIMIT) return false;
        Ack old = pending.get(sender);
        if (old != null && session < old.session) return false;
        if (old == null && pending.size() >= StreamSessions.MAX_SENDERS) return false;
        int previous = old != null && old.session == session ? old.bytes : 0;
        pending.put(
                sender,
                new Ack(
                        sender,
                        session,
                        (int) Math.min(FlowWindow.LIMIT, (long) previous + bytes)));
        return true;
    }

    public synchronized List<Ack> drain() {
        List<Ack> result = new ArrayList<>(pending.values());
        pending.clear();
        return result;
    }

    public synchronized void discardBefore(UUID sender, long session) {
        Ack old = pending.get(sender);
        if (old != null && old.session < session) pending.remove(sender);
    }

    public synchronized void remove(UUID sender) {
        pending.remove(sender);
    }

    public synchronized void clear() {
        pending.clear();
    }

    public synchronized int size() {
        return pending.size();
    }
}
