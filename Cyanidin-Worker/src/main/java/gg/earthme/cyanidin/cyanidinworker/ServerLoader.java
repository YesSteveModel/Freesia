package gg.earthme.cyanidin.cyanidinworker;

import com.mojang.logging.LogUtils;
import gg.earthme.cyanidin.cyanidinworker.impl.WorkerMessageHandlerImpl;
import i.mrhua269.cyanidin.common.EntryPoint;
import i.mrhua269.cyanidin.common.communicating.NettySocketClient;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;

public class ServerLoader implements DedicatedServerModInitializer {
    public static NettySocketClient clientInstance;
    public static volatile WorkerMessageHandlerImpl workerConnection;
    public static MinecraftServer SERVER_INST;

    @Override
    public void onInitializeServer() {
        EntryPoint.initLogger(LogUtils.getLogger());

        try {
            CyanidinWorkerConfig.init();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        clientInstance = new NettySocketClient(CyanidinWorkerConfig.masterServiceAddress, c -> {
            workerConnection = new WorkerMessageHandlerImpl();
            return workerConnection;
        });

        clientInstance.connect();
    }
}
