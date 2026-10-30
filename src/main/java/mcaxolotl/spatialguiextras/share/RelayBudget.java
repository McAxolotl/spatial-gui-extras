package mcaxolotl.spatialguiextras.share;

public final class RelayBudget {
    private double bytes = 512 * 1024, packets = 512;
    private long last;

    public synchronized boolean accept(int size, long now) {
        if (size <= 0 || size > ShareWire.MAX_PAYLOAD_BYTES) return false;
        if (last != 0) {
            double seconds = Math.max(0, (now - last) / 1e9);
            bytes = Math.min(512 * 1024, bytes + seconds * 8 * 1024 * 1024);
            packets = Math.min(512, packets + seconds * 8192);
        }
        last = now;
        if (bytes < size || packets < 1) return false;
        bytes -= size;
        packets--;
        return true;
    }
}
