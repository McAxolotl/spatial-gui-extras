package mcaxolotl.spatialguiextras.share;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LatestQueue<K, V> {
    private final LinkedHashMap<K, V> values = new LinkedHashMap<>();
    private final int limit;

    public LatestQueue(int limit) {
        this.limit = limit;
    }

    public synchronized boolean offer(K key, V value) {
        if (!values.containsKey(key) && values.size() >= limit) return false;
        values.put(key, value);
        return true;
    }

    public synchronized Map.Entry<K, V> first() {
        return values.isEmpty()
                ? null
                : Map.entry(
                        values.entrySet().iterator().next().getKey(),
                        values.values().iterator().next());
    }

    public synchronized boolean remove(K key, V value) {
        return values.remove(key, value);
    }

    public synchronized void removeIf(java.util.function.Predicate<K> predicate) {
        values.keySet().removeIf(predicate);
    }

    public synchronized void clear() {
        values.clear();
    }

    public synchronized int size() {
        return values.size();
    }
}
