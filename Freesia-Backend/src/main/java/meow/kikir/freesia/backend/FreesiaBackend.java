package meow.kikir.freesia.backend;

import meow.kikir.freesia.backend.misc.VirtualPlayerManager;
import meow.kikir.freesia.backend.tracker.TrackerProcessor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class FreesiaBackend extends JavaPlugin {
    public static FreesiaBackend INSTANCE;

    private final TrackerProcessor trackerProcessor = new TrackerProcessor();
    private final VirtualPlayerManager virtualPlayerManager = new VirtualPlayerManager();

    @Override
    public void onEnable() {
        INSTANCE = this;
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "freesia:tracker_sync", this.trackerProcessor);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "freesis:tracker_sync");
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "freesia:virtual_player_management", this.virtualPlayerManager);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "freesia:virtual_player_management");

        Bukkit.getPluginManager().registerEvents(this.trackerProcessor, this);
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, unused -> this.trackerProcessor.tickTracker(), 1, 1);
    }

    public VirtualPlayerManager getVirtualPlayerManager() {
        return this.virtualPlayerManager;
    }

    @Override
    public void onDisable() {
    }
}
