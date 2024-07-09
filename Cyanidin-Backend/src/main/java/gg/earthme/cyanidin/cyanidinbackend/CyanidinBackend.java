package gg.earthme.cyanidin.cyanidinbackend;

import gg.earthme.cyanidin.cyanidinbackend.tracker.TrackerProcessor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class CyanidinBackend extends JavaPlugin {
    public static CyanidinBackend INSTANCE;

    private final TrackerProcessor trackerProcessor = new TrackerProcessor();

    @Override
    public void onEnable() {
        INSTANCE = this;
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "cyanidin:tracker_sync", this.trackerProcessor);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "cyanidin:tracker_sync");
        Bukkit.getPluginManager().registerEvents(this.trackerProcessor, this);
    }

    @Override
    public void onDisable() {
    }
}
