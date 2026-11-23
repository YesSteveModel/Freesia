package meow.kikir.freesia.worker.mixin;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import meow.kikir.freesia.common.EntryPoint;
import meow.kikir.freesia.common.data.RequestedPlayerData;
import meow.kikir.freesia.common.utils.SimpleFriendlyByteBuf;
import meow.kikir.freesia.worker.ServerLoader;
import net.fabricmc.fabric.impl.networking.payload.PacketByteBufLoginQueryRequestPayload;
import net.fabricmc.fabric.impl.networking.payload.PacketByteBufLoginQueryResponse;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Mixin(value = ServerLoginPacketListenerImpl.class, priority = 100) // note: 100 to let us get inserted before fabric api
public abstract class ServerLoginPacketListenerImplMixin {
    @Unique
    private static final int FREESIA_LOGIN_CHANNEL_ID = -0x2fe4c2d; // Use a impossible value to prevent some packets coming through handle method incorrectly
    @Unique
    private static final ResourceLocation ID_FREESIA_PLUGIN_MESSAGE_CHANNEL = ResourceLocation.parse("freesia:login_channel");
    @Unique
    private final CompletableFuture<RequestedPlayerData> playerDataFetchCallback = new CompletableFuture<>();

    @Shadow
    @Nullable
    String requestedUsername;

    @Shadow
    abstract void startClientVerification(GameProfile gameProfile);

    @Shadow
    @Final
    Connection connection;

    @Shadow
    public abstract void disconnect(Component reason);

    @Shadow
    private volatile ServerLoginPacketListenerImpl.State state;

    @Shadow
    @Final
    private MinecraftServer server;

    @Shadow
    protected abstract void verifyLoginAndFinishConnectionSetup(GameProfile profile);

    @Shadow
    protected abstract void finishLoginAndWaitForClient(GameProfile profile);

    @Shadow
    @Nullable
    private GameProfile authenticatedProfile;

    @Shadow
    @Final
    private static Logger LOGGER;

    @Unique
    private void requestForPlayerData(@NotNull GameProfile profile) {
        final FriendlyByteBuf builtPayloadData = new FriendlyByteBuf(Unpooled.buffer());

        builtPayloadData.writeVarInt(0); // Action id (0 -> player data request)
        builtPayloadData.writeUUID(profile.getId()); // Target UUID

        final PacketByteBufLoginQueryRequestPayload builtRequestPayload = new PacketByteBufLoginQueryRequestPayload(ID_FREESIA_PLUGIN_MESSAGE_CHANNEL, builtPayloadData);
        final ClientboundCustomQueryPacket builtPacket = new ClientboundCustomQueryPacket(FREESIA_LOGIN_CHANNEL_ID, builtRequestPayload);

        this.connection.send(builtPacket);
    }


    @Inject(method = "disconnect", at = @At(value = "TAIL"))
    public void cleanUpCallbacks(Component reason, CallbackInfo ci) {
        if (!this.playerDataFetchCallback.isDone()) {
            this.playerDataFetchCallback.complete(null);
        }
    }

    @Inject(method = "handleCustomQueryPacket", at= @At(value = "HEAD"), cancellable = true)
    public void handlePluginMessageResponse(@NotNull ServerboundCustomQueryAnswerPacket packet, CallbackInfo ci) {
        final int id = packet.transactionId();
        final CustomQueryAnswerPayload payload = packet.payload();

        if (id != FREESIA_LOGIN_CHANNEL_ID) {
            return;
        }

        if (!(payload instanceof PacketByteBufLoginQueryResponse responsePayload)) {
            throw new IllegalStateException("Did y installed fabric api?");
        }

        ci.cancel();

        final FriendlyByteBuf payloadBuf = responsePayload.data();
        final SimpleFriendlyByteBuf converted = new SimpleFriendlyByteBuf(payloadBuf);

        final int action = converted.readVarInt();

        if (action == 0) {
            final RequestedPlayerData data = RequestedPlayerData.from(converted);

            this.playerDataFetchCallback.complete(data);

            LOGGER.info("Done data synchronization for {}", this.authenticatedProfile);
        } else {
            throw new UnsupportedOperationException("Unknow action id: " + action);
        }
    }

    @Unique
    public CompoundTag decodeNbtFrom(@NotNull RequestedPlayerData data) {
        final byte[] dataInBytes = data.getYsmNbtData();
        
        if (dataInBytes == null) {
            return null;
        }

        try {
            return  (CompoundTag) NbtIo.readAnyTag(new DataInputStream(new ByteArrayInputStream(dataInBytes)), NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            EntryPoint.LOGGER_INST.error("Error while decoding player data!", e);
            return null;
        }
    }

    /**
     * @author MrHua269
     * @reason Kill UUID checks and preload player data
     */
    @Overwrite
    public void handleHello(@NotNull ServerboundHelloPacket serverboundHelloPacket) {
        this.requestedUsername = serverboundHelloPacket.name();
        final GameProfile requestedProfile = new GameProfile(serverboundHelloPacket.profileId(), this.requestedUsername);

        if (ServerLoader.clientInstance == null || !ServerLoader.clientInstance.isReady()) {
            // however, we are doing this on netty thread, so there no need to force it back to the eventloop again
            this.connection.disconnect(Component.literal("Worker not ready yet!"));
            return;
        }

        EntryPoint.LOGGER_INST.info("Fetching player data for player {}.", requestedProfile.getName());

        this.requestForPlayerData(requestedProfile);

        // Preload it to prevent load it asynchronously
        this.playerDataFetchCallback.thenAcceptAsync(requestedPlayerData -> {
            if (requestedPlayerData == null) {
                this.disconnect(Component.literal("Connection terminated."));

                return;
            }

            final CompoundTag decodedNbt = this.decodeNbtFrom(requestedPlayerData);

            if (decodedNbt != null) {
                if (!requestedPlayerData.getRequestedPlayerUUID().equals(requestedProfile.getId())) {
                    this.disconnect(Component.literal("Received player data UUID does not match requested UUID!"));
                    return;
                }

                ServerLoader.playerDataCache.put(requestedProfile.getId(), decodedNbt);
            }


            ServerLoader.playerEntityIdMap.put(requestedProfile.getId(), requestedPlayerData.getEntityId());

            EntryPoint.LOGGER_INST.info("Pre-loaded player data for player {}.", requestedProfile.getName());

            // Directly bypass all checks
            this.authenticatedProfile = requestedProfile;
            this.finishLoginAndWaitForClient(requestedProfile);
        }, this.server);
    }
}
