package org.kirov.plugins.yinwutax.tax.headcount;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.kirov.plugins.yinwutax.storage.TaxDataSnapshot;

public class IpHistoryTracker implements Listener {

    private final TaxDataSnapshot snapshot;
    private final String salt;

    public IpHistoryTracker(TaxDataSnapshot snapshot, String salt) {
        this.snapshot = snapshot;
        this.salt = salt;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        InetAddress address = event.getAddress();
        if (address == null) {
            return;
        }

        record(event.getUniqueId(), hashIp(address.getHostAddress()));
    }

    public void record(UUID playerId, String hashedIp) {
        snapshot.getKnownAccounts().add(playerId);
        snapshot.getPlayerIpHashes()
            .computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet())
            .add(hashedIp);
        snapshot.getIpOwners()
            .computeIfAbsent(hashedIp, ignored -> ConcurrentHashMap.newKeySet())
            .add(playerId);
    }

    public int getLinkedAccountCount(UUID playerId) {
        return getLinkedAccounts(playerId).size();
    }

    public Set<UUID> getLinkedAccounts(UUID playerId) {
        Set<UUID> linked = new TreeSet<>();
        for (String hash : snapshot.getPlayerIpHashes().getOrDefault(playerId, Set.of())) {
            linked.addAll(snapshot.getIpOwners().getOrDefault(hash, Set.of()));
        }

        if (linked.isEmpty()) {
            linked.add(playerId);
        }
        return linked;
    }

    private String hashIp(String rawIp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((salt + ":" + rawIp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Missing SHA-256 algorithm", exception);
        }
    }
}
