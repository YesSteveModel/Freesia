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

        // TODO- De-hard-coding?
        Bukkit.getMessenger().registerIncomingPluginChannel(this, TrackerProcessor.CHANNEL_NAME, this.trackerProcessor);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, TrackerProcessor.CHANNEL_NAME);

        // TODO- De-hard-coding?
        Bukkit.getMessenger().registerIncomingPluginChannel(this, VirtualPlayerManager.CHANNEL_NAME, this.virtualPlayerManager);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, VirtualPlayerManager.CHANNEL_NAME);


        Bukkit.getPluginManager().registerEvents(this.trackerProcessor, this);
    }

    public VirtualPlayerManager getVirtualPlayerManager() {
        return this.virtualPlayerManager;
    }

    @Override
    public void onDisable() {
    }
}
