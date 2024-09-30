package gg.earthme.cyanidin.cyanidinbackend;

import gg.earthme.cyanidin.cyanidinbackend.misc.VirtualPlayerManager;
import gg.earthme.cyanidin.cyanidinbackend.tracker.TrackerProcessor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class CyanidinBackend extends JavaPlugin {
    public static CyanidinBackend INSTANCE;

    private final TrackerProcessor trackerProcessor = new TrackerProcessor();
    private final VirtualPlayerManager virtualPlayerManager = new VirtualPlayerManager();

    @Override
    public void onEnable() {
        INSTANCE = this;
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "cyanidin:tracker_sync", this.trackerProcessor);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "cyanidin:tracker_sync");
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "cyanidin:virtual_player_management", this.virtualPlayerManager);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "cyanidin:virtual_player_management");

        Bukkit.getPluginManager().registerEvents(this.trackerProcessor, this);
    }

    public VirtualPlayerManager getVirtualPlayerManager() {
        return this.virtualPlayerManager;
    }

    @Override
    public void onDisable() {
    }
}
