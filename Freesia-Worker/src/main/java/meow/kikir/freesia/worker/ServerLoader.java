package meow.kikir.freesia.worker;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.logging.LogUtils;
import meow.kikir.freesia.common.EntryPoint;
import meow.kikir.freesia.common.communicating.NettySocketClient;
import meow.kikir.freesia.worker.impl.WorkerMessageHandlerImpl;
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

    public static void connectToBackend() {
        EntryPoint.LOGGER_INST.info("Connecting to the master.");
        clientInstance.connect();
    }

    @Override
    public void onInitializeServer() {
        EntryPoint.initLogger(LogUtils.getLogger());

        try {
            FreesiaWorkerConfig.init();
            workerInfoFile = WorkerInfoFile.readOrCreate(new File("freesia_node_info.bin"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        playerDataCache = CacheBuilder
                .newBuilder()
                .expireAfterWrite(FreesiaWorkerConfig.playerDataCacheInvalidateIntervalSeconds, TimeUnit.SECONDS)
                .build();
        clientInstance = new NettySocketClient(FreesiaWorkerConfig.masterServiceAddress, c -> workerConnection = new WorkerMessageHandlerImpl(), FreesiaWorkerConfig.reconnectInterval) {
            @Override
            protected boolean shouldDoNextReconnect() {
                return SERVER_INST.isRunning();
            }
        };

        connectToBackend();
    }
}
