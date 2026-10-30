package mcaxolotl.spatialguiextras.share;

import java.util.*;

public final class RelayRoutes {
    private final StreamSessions sessions = new StreamSessions();
    private final Map<UUID, Map<UUID, Integer>> credits = new HashMap<>();

    public boolean open(UUID owner, long session, long now) {
        boolean changed = !sessions.active(owner, session);
        if (!sessions.open(owner, session, now)) return false;
        if (changed) credits.remove(owner);
        return true;
    }

    public boolean tile(UUID owner, long session, long now) {
        return sessions.tile(owner, session, now);
    }

    public boolean close(UUID owner, long session, long now) {
        if (!sessions.close(owner, session, now)) return false;
        credits.remove(owner);
        return true;
    }

    public boolean reserve(UUID owner, UUID viewer, long session, int bytes) {
        if (bytes <= 0 || bytes > FlowWindow.LIMIT || !sessions.active(owner, session))
            return false;
        Map<UUID, Integer> viewers = credits.computeIfAbsent(owner, id -> new HashMap<>());
        int pending = viewers.getOrDefault(viewer, 0);
        if ((pending == 0
                        && !viewers.containsKey(viewer)
                        && viewers.size() >= StreamSessions.MAX_SENDERS)
                || pending > FlowWindow.LIMIT - bytes) return false;
        viewers.put(viewer, pending + bytes);
        return true;
    }

    public boolean acknowledge(UUID owner, UUID viewer, long session, int bytes) {
        if (bytes <= 0 || !sessions.active(owner, session)) return false;
        Map<UUID, Integer> viewers = credits.get(owner);
        if (viewers == null) return false;
        int pending = viewers.getOrDefault(viewer, 0);
        if (bytes > pending) return false;
        if (pending == bytes) viewers.remove(viewer);
        else viewers.put(viewer, pending - bytes);
        if (viewers.isEmpty()) credits.remove(owner);
        return true;
    }

    public void retainViewers(UUID owner, Set<UUID> reachable) {
        Map<UUID, Integer> viewers = credits.get(owner);
        if (viewers == null) return;
        viewers.keySet().retainAll(reachable);
        if (viewers.isEmpty()) credits.remove(owner);
    }

    public void expire(long now) {
        for (UUID owner : sessions.expire(now)) credits.remove(owner);
    }

    public void remove(UUID id) {
        sessions.remove(id);
        credits.remove(id);
        var iterator = credits.values().iterator();
        while (iterator.hasNext()) {
            Map<UUID, Integer> viewers = iterator.next();
            viewers.remove(id);
            if (viewers.isEmpty()) iterator.remove();
        }
    }

    public int routeCount() {
        return credits.values().stream().mapToInt(Map::size).sum();
    }

    public int senderCount() {
        return sessions.size();
    }
}
