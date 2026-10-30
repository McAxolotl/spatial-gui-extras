package mcaxolotl.spatialguiextras.share;

import java.util.*;

public final class StreamSessions {
    public static final int MAX_SENDERS = 256;
    public static final long IDLE_NANOS = 5_000_000_000L;

    private static final class State {
        long session, lastSeen;
        boolean closed;

        State(long session, long now) {
            this.session = session;
            this.lastSeen = now;
        }
    }

    private final Map<UUID, State> senders = new HashMap<>();

    public synchronized boolean open(UUID sender, long session, long now) {
        if (session <= 0) return false;
        State state = senders.get(sender);
        if (state == null) {
            if (senders.size() >= MAX_SENDERS) return false;
            senders.put(sender, new State(session, now));
            return true;
        }
        if (session < state.session || (session == state.session && state.closed)) return false;
        if (session > state.session) {
            state.session = session;
            state.closed = false;
        }
        state.lastSeen = now;
        return true;
    }

    public synchronized boolean tile(UUID sender, long session, long now) {
        State state = senders.get(sender);
        if (state == null || state.closed || state.session != session) return false;
        state.lastSeen = now;
        return true;
    }

    public synchronized boolean close(UUID sender, long session, long now) {
        State state = senders.get(sender);
        if (state == null || state.session != session) return false;
        state.closed = true;
        state.lastSeen = now;
        return true;
    }

    public synchronized boolean active(UUID sender, long session) {
        State state = senders.get(sender);
        return state != null && !state.closed && state.session == session;
    }

    public synchronized Set<UUID> expire(long now) {
        Set<UUID> expired = new HashSet<>();
        senders.entrySet()
                .removeIf(
                        entry -> {
                            if (now - entry.getValue().lastSeen > IDLE_NANOS) {
                                expired.add(entry.getKey());
                                return true;
                            }
                            return false;
                        });
        return expired;
    }

    public synchronized void remove(UUID sender) {
        senders.remove(sender);
    }

    public synchronized void clear() {
        senders.clear();
    }

    public synchronized int size() {
        return senders.size();
    }
}
