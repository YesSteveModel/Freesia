package meow.kikir.freesia.backend;

import meow.kikir.freesia.backend.tracker.TrackerProcessor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class FreesiaBackend extends JavaPlugin {
    public static FreesiaBackend INSTANCE;

    private final TrackerProcessor trackerProcessor = new TrackerProcessor();


    @Override
    public void onEnable() {
        INSTANCE = this;

        // TODO- De-hard-coding?
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, TrackerProcessor.CHANNEL_NAME);


        Bukkit.getPluginManager().registerEvents(this.trackerProcessor, this);
    }


    @Override
    public void onDisable() {
    }
}
