package gg.earthme.cyanidin.cyanidinworker;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.logging.LogUtils;
import gg.earthme.cyanidin.cyanidinworker.impl.WorkerMessageHandlerImpl;
import gg.earthme.cyanidin.common.EntryPoint;
import gg.earthme.cyanidin.common.communicating.NettySocketClient;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ServerLoader implements DedicatedServerModInitializer {
    public static NettySocketClient clientInstance;
    public static volatile WorkerMessageHandlerImpl workerConnection = new WorkerMessageHandlerImpl();
    public static MinecraftServer SERVER_INST;
    public static WorkerInfoFile workerInfoFile;
    public static Cache<UUID, CompoundTag> playerDataCache;

    @Override
    public void onInitializeServer() {
        EntryPoint.initLogger(LogUtils.getLogger());

        try {
            CyanidinWorkerConfig.init();
            workerInfoFile = WorkerInfoFile.readOrCreate(new File("cyanidin_node_info.bin"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        playerDataCache = CacheBuilder.newBuilder().expireAfterWrite(CyanidinWorkerConfig.playerDataCacheInvalidateIntervalSeconds, TimeUnit.SECONDS).build();
        clientInstance = new NettySocketClient(CyanidinWorkerConfig.masterServiceAddress, c -> workerConnection, CyanidinWorkerConfig.reconnectInterval);

        connectToBackend();
    }

    public static void connectToBackend(){
        EntryPoint.LOGGER_INST.info("Connecting to the master.");
        clientInstance.connect();
    }
}
