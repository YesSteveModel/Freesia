package meow.kikir.freesia.worker;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.logging.LogUtils;
import meow.kikir.freesia.common.EntryPoint;
import meow.kikir.freesia.common.communicating.NettySocketClient;
import meow.kikir.freesia.common.data.RequestedPlayerData;
import meow.kikir.freesia.worker.impl.WorkerMessageHandlerImpl;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ServerLoader implements DedicatedServerModInitializer {
    public static NettySocketClient clientInstance;
    public static volatile WorkerMessageHandlerImpl workerConnection = new WorkerMessageHandlerImpl(null);

    public static MinecraftServer SERVER_INST;
    public static WorkerInfoFile workerInfoFile;

    public static Cache<UUID, CompoundTag> playerDataCache;
    public static final Map<UUID, Integer> playerEntityIdMap = new ConcurrentHashMap<>();

    public static void connectToBackend() {
        EntryPoint.LOGGER_INST.info("Connecting to the master.");
        clientInstance.connect();
    }

    @Override
    public void onInitializeServer() {
        doInit();
    }

    public static void doInit() {
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
        clientInstance = new NettySocketClient(FreesiaWorkerConfig.masterServiceAddress, channel -> workerConnection = new WorkerMessageHandlerImpl(channel), FreesiaWorkerConfig.reconnectInterval) {
            @Override
            protected boolean shouldDoNextReconnect() {
                return SERVER_INST == null || SERVER_INST.isRunning();
            }
        };

        clientInstance.attachShutdownHook();

        connectToBackend();
    }
}
