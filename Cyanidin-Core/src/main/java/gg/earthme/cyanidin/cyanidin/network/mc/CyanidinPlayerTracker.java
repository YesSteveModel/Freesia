package gg.earthme.cyanidin.cyanidin.network.mc;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class CyanidinPlayerTracker implements Listener {
    private final Set<BiConsumer<Player, Player>> listeners = ConcurrentHashMap.newKeySet();
    private final Map<Player, Set<Player>> visibleMap = new ConcurrentHashMap<>();

    public void deRegisterAll(){
        this.listeners.clear();
    }

    public CompletableFuture<Set<Player>> getCanSeeAsync(@NotNull Player target){
        final Set<Player> result = new HashSet<>();

        for (Player p : Bukkit.getOnlinePlayers()){
            if (p == target) continue;

            if (p.canSee(target)){
                result.add(p);
            }
        }

        return CompletableFuture.completedFuture(result);
    }

    public void addTrackerEventListener(BiConsumer<Player, Player> listener) {
        this.listeners.add(listener);
    }

    @EventHandler
    public void onPlayerLeft(@NotNull PlayerQuitEvent event){
        this.visibleMap.remove(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDead(@NotNull PlayerDeathEvent event){
        this.visibleMap.remove(event.getEntity());
        for (Set<Player> others : this.visibleMap.values()){
            others.remove(event.getEntity());
        }
    }

    @EventHandler
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event){
        final Player player = event.getPlayer();

        this.visibleMap.put(player, new HashSet<>());
        this.visibleMap.get(player).add(player);

        Set<Player> visiblePlayers = new HashSet<>();

        visiblePlayers.add(player);

        for (Player singlePlayer : Bukkit.getOnlinePlayers()) {
            if (player.canSee(singlePlayer)) {
                visiblePlayers.add(singlePlayer);
                this.playerTrackedPlayer(player, singlePlayer);
            } else {
                if (this.visibleMap.containsKey(player) && this.visibleMap.get(player).contains(singlePlayer)) {
                    this.visibleMap.get(player).remove(singlePlayer);
                }
            }
        }

        this.visibleMap.replace(player, visiblePlayers);
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event){
        this.visibleMap.put(event.getPlayer(), new HashSet<>());
        this.visibleMap.get(event.getPlayer()).add(event.getPlayer());
    }

    @EventHandler
    public void onPlayerMove(@NotNull PlayerMoveEvent event){
        final Player player = event.getPlayer();
        Set<Player> visiblePlayers = new HashSet<>();

        visiblePlayers.add(player);

        for (Player singlePlayer : Bukkit.getOnlinePlayers()) {
            if (player.canSee(singlePlayer)) {
                visiblePlayers.add(singlePlayer);
                this.playerTrackedPlayer(player, singlePlayer);
            } else {
                if (this.visibleMap.containsKey(player) && this.visibleMap.get(player).contains(singlePlayer)) {
                    this.visibleMap.get(player).remove(singlePlayer);
                }
            }
        }

        this.visibleMap.replace(player, visiblePlayers);
    }

    private void playerTrackedPlayer(@NotNull Player watcher, @NotNull Player beSeeing){
        for (BiConsumer<Player, Player> listener : this.listeners){
            listener.accept(watcher, beSeeing);
        }
    }
}