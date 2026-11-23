package meow.kikir.freesia.worker.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import meow.kikir.freesia.common.EntryPoint;
import meow.kikir.freesia.common.communicating.NettySocketClient;
import meow.kikir.freesia.common.communicating.handler.ClientChannelHandlerBase;
import meow.kikir.freesia.common.communicating.message.w2m.W2MUpdatePlayerDataRequestMessage;
import meow.kikir.freesia.common.communicating.message.w2m.W2MWorkerInfoMessage;
import meow.kikir.freesia.worker.Constants;
import meow.kikir.freesia.worker.ServerLoader;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

public class WorkerMessageHandlerImpl extends ClientChannelHandlerBase {

    public WorkerMessageHandlerImpl(Channel channel) {
        super(channel);
    }

    @Override
    public void channelActive(@NotNull ChannelHandlerContext ctx) {
        ServerLoader.workerConnection = this;

        super.channelActive(ctx);

        this.sendToMaster(new W2MWorkerInfoMessage(ServerLoader.workerInfoFile.workerUUID(), ServerLoader.workerInfoFile.workerName()));

        this.cleanModelFolder(Constants.MODELS_PATH_ALL);
    }

    @Override
    public void channelInactive(@NotNull ChannelHandlerContext ctx) {
        super.channelInactive(ctx);

        ServerLoader.connectToBackend();
    }

    @Override
    public NettySocketClient getClient() {
        return ServerLoader.clientInstance;
    }

    @Override
    public CompletableFuture<String> dispatchCommand(String command) {
        final CompletableFuture<String> callback = new CompletableFuture<>();

        Runnable scheduledCommand = () -> {
            CommandDispatcher<CommandSourceStack> commandDispatcher = ServerLoader.SERVER_INST.getCommands().getDispatcher();

            final ParseResults<CommandSourceStack> parsed = commandDispatcher.parse(command, ServerLoader.SERVER_INST.createCommandSourceStack().withSource(new CommandSource() {
                @Override
                public void sendSystemMessage(Component component) {
                    callback.complete(component.getString());
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                @Override
                public boolean shouldInformAdmins() {
                    return false;
                }
            }));

            ServerLoader.SERVER_INST.getCommands().performCommand(parsed, command);
        };

        if (ServerLoader.SERVER_INST == null) {
            callback.completeExceptionally(new RejectedExecutionException());
            return callback;
        }

        ServerLoader.SERVER_INST.execute(scheduledCommand);

        return callback;
    }

    @Override
    public void handleReadyNotification() {
        this.getClient().onReady();

        // not on first start
        if (ServerLoader.SERVER_INST != null) {
            this.callYsmModelReload();
        }
    }

    @Override
    public void callYsmModelReload() {
        EntryPoint.LOGGER_INST.info("Calling /ysm model reload");

        final String builtCommand = "ysm model reload";

        Runnable scheduledCommand = () -> {
            CommandDispatcher<CommandSourceStack> commandDispatcher = ServerLoader.SERVER_INST.getCommands().getDispatcher();

            final ParseResults<CommandSourceStack> parsed = commandDispatcher.parse(builtCommand, ServerLoader.SERVER_INST.createCommandSourceStack().withSource(new CommandSource() {
                @Override
                public void sendSystemMessage(Component component) {
                    EntryPoint.LOGGER_INST.info("Ysm reload response: {}", component.getString());
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                @Override
                public boolean shouldInformAdmins() {
                    return false;
                }
            }));

            ServerLoader.SERVER_INST.getCommands().performCommand(parsed, builtCommand);
        };

        ServerLoader.SERVER_INST.execute(scheduledCommand);
    }

    public void updatePlayerData(UUID playerUUID, CompoundTag data) {
        try {
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final DataOutputStream dos = new DataOutputStream(bos);

            NbtIo.writeAnyTag(data, dos);
            dos.flush();

            final byte[] content = bos.toByteArray();

            this.sendToMaster(new W2MUpdatePlayerDataRequestMessage(playerUUID, content));
        } catch (Exception e) {
            EntryPoint.LOGGER_INST.error("Failed to encode nbt!", e);
        }
    }
}
