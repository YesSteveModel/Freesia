package meow.kikir.freesia.velocity.network.ysm;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.velocitypowered.api.proxy.Player;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.network.mc.NbtRemapper;
import meow.kikir.freesia.velocity.network.mc.impl.StandardNbtRemapperImpl;
import meow.kikir.freesia.velocity.utils.FriendlyByteBuf;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

public class VirtualYsmPacketProxyImpl implements YsmPacketProxy {
    private final UUID virtualPlayerUUID;
    private final NbtRemapper nbtRemapper = new StandardNbtRemapperImpl();
    private volatile NBTCompound lastYsmEntityStatus = null;
    private volatile int playerEntityId = -1;

    public VirtualYsmPacketProxyImpl(UUID virtualPlayerUUID) {
        this.virtualPlayerUUID = virtualPlayerUUID;
    }

    @Override
    public ProxyComputeResult processS2C(Key channelKey, ByteBuf copiedPacketData) {
        return null;
    }

    @Override
    public ProxyComputeResult processC2S(Key channelKey, ByteBuf copiedPacketData) {
        return null;
    }

    @Override
    public Player getOwner() {
        return null;
    }

    @Override
    public void setEntityDataRaw(NBTCompound data) {
        this.lastYsmEntityStatus = data;
    }

    @Override
    public void refreshToOthers() {
        final NBTCompound entityStatus = this.lastYsmEntityStatus; // Copy the value

        // If the entity dose not have any data
        if (entityStatus == null) {
            return;
        }

        Freesia.tracker.getCanSee(this.virtualPlayerUUID).whenComplete((beingWatched, exception) -> { // Async tracker check request to backend server
            if (beingWatched != null) { // Actually there is impossible to be null
                for (UUID targetUUID : beingWatched) {
                    final Optional<Player> targetNullable = Freesia.PROXY_SERVER.getPlayer(targetUUID);

                    if (targetNullable.isPresent()) { // Skip send to NPCs
                        final Player target = targetNullable.get();

                        if (!Freesia.mapperManager.isPlayerInstalledYsm(target)) { // Skip if target is not ysm-installed
                            continue;
                        }

                        this.sendEntityStateToInternal(target, entityStatus); // Sync to target
                    }
                }
            }
        });
    }

    @Override
    public NBTCompound getCurrentEntityState() {
        return this.lastYsmEntityStatus;
    }

    @Override
    public void setPlayerWorkerEntityId(int id) {
        // We are no mapper on the worker
    }

    @Override
    public void setPlayerEntityId(int id) {
        final int oldValue = this.playerEntityId;

        this.playerEntityId = id;

        // State changed
        if (oldValue != id) {
            this.refreshToOthers();
        }
    }

    @Override
    public int getPlayerEntityId() {
        return this.playerEntityId;
    }

    @Override
    public int getPlayerWorkerEntityId() {
        return -1; // No worker entity id
    }

    @Override
    public void sendEntityStateTo(@NotNull Player target) {
        this.sendEntityStateToInternal(target, this.lastYsmEntityStatus);
    }

    private void sendEntityStateToInternal(Player target, NBTCompound entityStatus) {
        final int currentEntityId = this.playerEntityId; // Get current entity id on the server of the player

        if (entityStatus == null || currentEntityId == -1) { // If no data got or player is not in the backend server currently
            return;
        }

        try {
            final Object targetChannel = PacketEvents.getAPI().getProtocolManager().getChannel(target.getUniqueId()); // Get the netty channel of the player

            if (targetChannel == null) {
                return;
            }

            final ClientVersion clientVersion = PacketEvents.getAPI().getProtocolManager().getClientVersion(targetChannel); // Get the client version of the player

            final int targetProtocolVer = clientVersion.getProtocolVersion(); // Protocol version(Used for nbt remappers)
            final FriendlyByteBuf wrappedPacketData = new FriendlyByteBuf(Unpooled.buffer());

            wrappedPacketData.writeByte(4);
            wrappedPacketData.writeVarInt(currentEntityId);
            wrappedPacketData.writeBytes(this.nbtRemapper.shouldRemap(targetProtocolVer) ? this.nbtRemapper.remapToMasterVer(entityStatus) : this.nbtRemapper.remapToWorkerVer(entityStatus)); // Remap nbt if needed

            this.sendPluginMessageTo(target, YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY, wrappedPacketData);
        } catch (Exception e) {
            Freesia.LOGGER.error("Error in encoding nbt or sending packet!", e);
        }
    }
}
