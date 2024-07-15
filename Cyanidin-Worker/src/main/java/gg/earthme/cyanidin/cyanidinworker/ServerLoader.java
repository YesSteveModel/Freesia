package gg.earthme.cyanidin.cyanidinworker;

import com.elfmcys.yesstevemodel.*;
import com.mojang.logging.LogUtils;
import gg.earthme.cyanidin.cyanidinworker.impl.WorkerMessageHandlerImpl;
import i.mrhua269.cyanidin.common.EntryPoint;
import i.mrhua269.cyanidin.common.communicating.NettySocketClient;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;

public class ServerLoader implements DedicatedServerModInitializer {
    public static NettySocketClient clientInstance;
    public static volatile WorkerMessageHandlerImpl workerConnection;

    @Override
    public void onInitializeServer() {
        EntryPoint.initLogger(LogUtils.getLogger());
        //YesSteveModel
        //oooOo0o0o00O0oOooooOO0Oo //Data section interface
        //O0OoOOOO0oOOO0OOoOOO0OOO //Entity status packet //TODO Tracker
    }
}
