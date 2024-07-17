package gg.earthme.cyanidin.cyanidin.network.mc;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import gg.earthme.cyanidin.cyanidin.Cyanidin;
import gg.earthme.cyanidin.cyanidin.utils.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CyanidinPlayerTracker {
    private static final MinecraftChannelIdentifier SYNC_CHANNEL_KEY = MinecraftChannelIdentifier.create("cyanidin", "tracker_sync");

    private final Set<BiConsumer<Player, Player>> listeners = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Consumer<Set<Player>>> pendingCanSeeTasks = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(0);

    public void init(){
        Cyanidin.PROXY_SERVER.getChannelRegistrar().register(SYNC_CHANNEL_KEY);
        Cyanidin.PROXY_SERVER.getEventManager().register(Cyanidin.INSTANCE, this);
    }

    @Subscribe
    public void onChannelMsg(@NotNull PluginMessageEvent event){
        if (!(event.getSource() instanceof ServerConnection)){
            return;
        }

        if (!event.getIdentifier().getId().equals(SYNC_CHANNEL_KEY.getId())){
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        final FriendlyByteBuf packetData = new FriendlyByteBuf(Unpooled.wrappedBuffer(event.getData()));

        switch (packetData.readVarInt()){
            case 0 -> {
                final int taskId = packetData.readVarInt();
                final int collectionSize = packetData.readVarInt();
                final Set<Player> result = new HashSet<>(collectionSize);

                for (int i = 0; i < collectionSize; i++) {
                    final Optional<Player> target = Cyanidin.PROXY_SERVER.getPlayer(packetData.readUUID());

                    target.ifPresent(result::add);
                }

                final Consumer<Set<Player>> targetTask = this.pendingCanSeeTasks.remove(taskId);

                try {
                    targetTask.accept(result);
                }catch (Exception e){
                    Cyanidin.LOGGER.error("Can not process tracker callback task !", e);
                }
            }

            case 2 -> {
                final UUID beSeeing = packetData.readUUID();
                final UUID watcher = packetData.readUUID();

                final Optional<Player> watcherPlayerNullable = Cyanidin.PROXY_SERVER.getPlayer(watcher);
                final Optional<Player> beSeeingPlayerNullable = Cyanidin.PROXY_SERVER.getPlayer(beSeeing);

                if (watcherPlayerNullable.isPresent() && beSeeingPlayerNullable.isPresent()){
                    final Player watcherPlayer = watcherPlayerNullable.get();
                    final Player beSeeingPlayer = beSeeingPlayerNullable.get();

                    for (BiConsumer<Player, Player> listener : this.listeners){
                        try {
                            listener.accept(beSeeingPlayer, watcherPlayer);
                        }catch (Exception e){
                            Cyanidin.LOGGER.error("Can not process tracker update!", e);
                        }
                    }
                }
            }
        }
    }

    public CompletableFuture<Set<Player>> getCanSeeAsync(@NotNull Player target){
        CompletableFuture<Set<Player>> callback = new CompletableFuture<>();
        final int callbackId = this.idGenerator.getAndIncrement();

        this.pendingCanSeeTasks.put(callbackId, callback::complete);

        final FriendlyByteBuf callbackRequest = new FriendlyByteBuf(Unpooled.buffer());

        callbackRequest.writeVarInt(1);
        callbackRequest.writeVarInt(callbackId);
        callbackRequest.writeUUID(target.getUniqueId());

        target.getCurrentServer().ifPresentOrElse(server -> server.getServer().sendPluginMessage(SYNC_CHANNEL_KEY, callbackRequest.array()), () -> { throw new IllegalStateException(); });

        return callback;
    }

    public Set<Player> getCanSee(@NotNull Player target) {
        CompletableFuture<Set<Player>> callback = new CompletableFuture<>();
        final int callbackId = this.idGenerator.getAndIncrement();

        this.pendingCanSeeTasks.put(callbackId, callback::complete);

        final FriendlyByteBuf callbackRequest = new FriendlyByteBuf(Unpooled.buffer());

        callbackRequest.writeVarInt(1);
        callbackRequest.writeVarInt(callbackId);
        callbackRequest.writeUUID(target.getUniqueId());

        target.getCurrentServer().ifPresentOrElse(server -> server.getServer().sendPluginMessage(SYNC_CHANNEL_KEY, callbackRequest.array()), () -> { throw new IllegalStateException(); });

        return callback.join();
    }

    public void addTrackerEventListener(BiConsumer<Player, Player> listener) {
        this.listeners.add(listener);
    }
}