package meow.kikir.freesia.velocity.network.ysm;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.proxy.Player;
import io.netty.buffer.Unpooled;
import meow.kikir.freesia.velocity.FreesiaConstants;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.YsmProtocolMetaFile;
import meow.kikir.freesia.velocity.events.PlayerYsmHandshakeEvent;
import meow.kikir.freesia.velocity.events.PlayerEntityStateChangeEvent;
import meow.kikir.freesia.velocity.utils.FriendlyByteBuf;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RealPlayerYsmPacketProxyImpl extends YsmPacketProxyLayer{

    public RealPlayerYsmPacketProxyImpl(Player player) {
        super(player);
    }

    @Override
    public CompletableFuture<Set<UUID>> fetchTrackerList(UUID observer) {
        return Freesia.tracker.getCanSee(observer);
    }


    @Override
    public ProxyComputeResult processS2C(Key key, ByteBuf copiedPacketData) {
        final FriendlyByteBuf mcBuffer = new FriendlyByteBuf(copiedPacketData);
        final byte packetId = mcBuffer.readByte();

        if (packetId == YsmProtocolMetaFile.getS2CPacketId(FreesiaConstants.YsmProtocolMetaConstants.Clientbound.ENTITY_DATA_UPDATE)) {
            final int workerEntityId = mcBuffer.readVarInt();

            if (!this.isEntityStateOfSelf(workerEntityId)) { // Check if the packet is current player and drop to prevent incorrect broadcasting
                return ProxyComputeResult.ofDrop(); // Do not process the entity state if it is not ours
            }

            try {
                Freesia.PROXY_SERVER.getEventManager().fire(new PlayerEntityStateChangeEvent(this.player,workerEntityId, this.nbtRemapper.readBound(mcBuffer))).thenAccept(result -> { // Use NbtRemapper for multi version clients
                    final NBTCompound to = result.getEntityState();

                    this.acquireWriteReference(); // Acquire write reference

                    LAST_YSM_ENTITY_DATA_HANDLE.setVolatile(this, to);

                    this.releaseWriteReference(); // Release write reference

                    this.notifyFullTrackerUpdates(); // Notify updates
                }).join(); // Force blocking as we do not want to break the sequence of the data
            }catch (Exception e){
                Freesia.LOGGER.error("Failed to process entity state update packet", e);
            }

            return ProxyComputeResult.ofDrop();
        }

        if (packetId == YsmProtocolMetaFile.getS2CPacketId(FreesiaConstants.YsmProtocolMetaConstants.Clientbound.HAND_SHAKE_CONFIRMED)) {
            final String backendVersion = mcBuffer.readUtf();
            final boolean canSwitchModel = mcBuffer.readBoolean();

            Freesia.LOGGER.info("Replying ysm client with server version {}.Can switch model? : {}", backendVersion, canSwitchModel);

            return ProxyComputeResult.ofPass();
        }

        if (packetId == YsmProtocolMetaFile.getS2CPacketId(FreesiaConstants.YsmProtocolMetaConstants.Clientbound.MOLANG_EXECUTE)) {
            final int[] entityIds = mcBuffer.readVarIntArray();
            final int[] entityIdsRemapped = new int[entityIds.length];
            final String expression = mcBuffer.readUtf();

            final Map<Integer, RealPlayerYsmPacketProxyImpl> collectedPaddingWorkerEntityId = Freesia.mapperManager.collectRealProxy2WorkerEntityId();

            // remap the entity id
            int idx = 0;
            for (int singleWorkerEntityId : entityIds) {
                final RealPlayerYsmPacketProxyImpl targetProxy = collectedPaddingWorkerEntityId.get(singleWorkerEntityId);

                if (targetProxy == null) {
                    continue;
                }

                entityIdsRemapped[idx] = targetProxy.getPlayerEntityId(); // we are on backend side
                idx++;
            }

            // re-send packet as it's much cheaper than modify
            this.executeMolang(entityIdsRemapped, expression);
            return ProxyComputeResult.ofDrop();
        }

        return ProxyComputeResult.ofPass();
    }

    @Override
    public ProxyComputeResult processC2S(Key key, ByteBuf copiedPacketData) {
        final FriendlyByteBuf mcBuffer = new FriendlyByteBuf(copiedPacketData);
        final byte packetId = mcBuffer.readByte();

        if (packetId == YsmProtocolMetaFile.getC2SPacketId(FreesiaConstants.YsmProtocolMetaConstants.Serverbound.HAND_SHAKE_REQUEST)) {
            final ResultedEvent.GenericResult result = Freesia.PROXY_SERVER.getEventManager().fire(new PlayerYsmHandshakeEvent(this.player)).join().getResult();

            if (!result.isAllowed()) {
                return ProxyComputeResult.ofDrop();
            }

            final String clientYsmVersion = mcBuffer.readUtf();
            Freesia.LOGGER.info("Player {} is connected to the backend with ysm version {}", this.player.getUsername(), clientYsmVersion);
            Freesia.mapperManager.onClientYsmHandshakePacketReply(this.player);
        }

        if (packetId == YsmProtocolMetaFile.getC2SPacketId(FreesiaConstants.YsmProtocolMetaConstants.Serverbound.MOLANG_EXECUTE_REQ)) {
            final String molangExpression = mcBuffer.readUtf();
            final int entityId = mcBuffer.readVarInt();
            final int currWorkerEntityId = this.getPlayerWorkerEntityId();

            if (currWorkerEntityId != -1) {
                final FriendlyByteBuf newPacketByteBuf = new FriendlyByteBuf(Unpooled.buffer());
                newPacketByteBuf.writeByte(YsmProtocolMetaFile.getC2SPacketId(FreesiaConstants.YsmProtocolMetaConstants.Serverbound.MOLANG_EXECUTE_REQ));
                newPacketByteBuf.writeUtf(molangExpression);
                newPacketByteBuf.writeVarInt(currWorkerEntityId);
                return ProxyComputeResult.ofModify(newPacketByteBuf);
            }
        }

        return ProxyComputeResult.ofPass();
    }
}
