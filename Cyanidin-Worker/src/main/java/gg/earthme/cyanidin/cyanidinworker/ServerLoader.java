package gg.earthme.cyanidin.cyanidinworker;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.logging.LogUtils;
import gg.earthme.cyanidin.cyanidinworker.impl.WorkerMessageHandlerImpl;
import i.mrhua269.cyanidin.common.EntryPoint;
import i.mrhua269.cyanidin.common.communicating.NettySocketClient;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ServerLoader implements DedicatedServerModInitializer {
    public static NettySocketClient clientInstance;
    public static volatile WorkerMessageHandlerImpl workerConnection = new WorkerMessageHandlerImpl();
    public static MinecraftServer SERVER_INST;

    public static final Cache<UUID, CompoundTag> playerDataCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build();

    @Override
    public void onInitializeServer() {
        EntryPoint.initLogger(LogUtils.getLogger());

        try {
            CyanidinWorkerConfig.init();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        clientInstance = new NettySocketClient(CyanidinWorkerConfig.masterServiceAddress, c -> workerConnection, CyanidinWorkerConfig.reconnectInterval);

        connectToBackend();
    }

    public static void connectToBackend(){
        EntryPoint.LOGGER_INST.info("Connecting to the master.");
        clientInstance.connect();
    }
}
