package mcaxolotl.spatialguiextras;

import mcaxolotl.spatialguiextras.share.OverlayPose;
import mcaxolotl.spatialguiextras.share.RelayBudget;
import mcaxolotl.spatialguiextras.share.RelayRoutes;
import mcaxolotl.spatialguiextras.share.ShareProtocol;
import mcaxolotl.spatialguiextras.share.ShareWire;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.class_2540;
import net.minecraft.class_3222;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Extras implements ModInitializer {
    private final Map<UUID, RelayBudget> budgets = new ConcurrentHashMap<>();
    private final Map<UUID, Long> warnings = new ConcurrentHashMap<>();
    private final RelayRoutes routes = new RelayRoutes();
    private long lastPrune;

    @Override
    public void onInitialize() {
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> {
                    UUID id = handler.field_14140.method_5667();
                    budgets.remove(id);
                    warnings.remove(id);
                    routes.remove(id);
                });
        ServerPlayNetworking.registerGlobalReceiver(
                ShareWire.CHANNEL,
                (server, player, handler, buf, sender) -> {
                    UUID id = player.method_5667();
                    if (!budgets.computeIfAbsent(id, key -> new RelayBudget())
                            .accept(buf.readableBytes(), System.nanoTime())) {
                        warn(id, "rate limit");
                        return;
                    }
                    int start = buf.readerIndex();
                    ShareProtocol.Message message;
                    try {
                        message = ShareProtocol.read(buf);
                    } catch (RuntimeException e) {
                        warn(id, "invalid message: " + e.getMessage());
                        return;
                    }
                    byte[] body = new byte[buf.readerIndex() - start];
                    buf.getBytes(start, body);
                    server.execute(
                            () -> {
                                if (PlayerLookup.all(server).contains(player))
                                    relay(player, message, body);
                            });
                });
        System.out.println(
                "[Spatial GUI Extras] Shared GUI protocol v3 relay ready (validated and rate limited)");
    }

    private void relay(class_3222 owner, ShareProtocol.Message message, byte[] body) {
        var world = owner.method_51469();
        if (world == null) return;
        UUID id = owner.method_5667();
        long now = System.nanoTime();
        if (now - lastPrune > 1_000_000_000L) {
            routes.expire(now);
            lastPrune = now;
        }
        if (message.type() == ShareWire.ACK) {
            if (!routes.acknowledge(message.owner(), id, message.session(), message.bytes()))
                return;
        } else if (message.type() == ShareWire.POSE) {
            if (!routes.open(id, message.session(), now)) {
                warn(id, "stale session or sender capacity");
                return;
            }
        } else if (message.type() == ShareWire.CLEAR) {
            if (!routes.close(id, message.session(), now)) return;
        } else if (!routes.tile(id, message.session(), now)) {
            warn(id, "tile without an active announced session");
            return;
        }
        var targets = PlayerLookup.around(world, owner.method_19538(), ShareWire.RELAY_RADIUS);
        if (message.type() != ShareWire.ACK) {
            java.util.Set<UUID> live = new java.util.HashSet<>();
            for (class_3222 target : targets)
                if (!target.method_5667().equals(id)
                        && ServerPlayNetworking.canSend(target, ShareWire.CHANNEL))
                    live.add(target.method_5667());
            routes.retainViewers(id, live);
        }
        for (class_3222 target : targets) {
            UUID targetId = target.method_5667();
            if (targetId.equals(id)
                    || (message.type() == ShareWire.ACK && !targetId.equals(message.owner()))
                    || !ServerPlayNetworking.canSend(target, ShareWire.CHANNEL)) continue;
            if (message.type() == ShareWire.TILE
                    || (message.type() == ShareWire.OVERLAY
                            && message.data().length > OverlayPose.BYTES)) {
                if (!routes.reserve(id, targetId, message.session(), message.data().length + 48))
                    continue;
            }
            class_2540 output = PacketByteBufs.create();
            output.method_10797(id);
            output.writeBytes(body);
            ServerPlayNetworking.send(target, ShareWire.CHANNEL, output);
        }
    }

    private void warn(UUID id, String reason) {
        long now = System.nanoTime();
        Long previous = warnings.putIfAbsent(id, now);
        if (previous == null || now - previous > 1_000_000_000L) {
            warnings.put(id, now);
            System.err.println(
                    "[Spatial GUI Extras share] Rejected packet from " + id + ": " + reason);
        }
    }
}
