package meow.kikir.freesia.velocity.events;

import com.velocitypowered.api.proxy.Player;
import meow.kikir.freesia.velocity.network.ysm.ProxyComputeResult;
import meow.kikir.freesia.velocity.network.ysm.protocol.YsmPacket;
import org.jetbrains.annotations.NotNull;

public class PlayerKnownYsmPacketOutputEvent {
    private YsmPacket packet;
    private ProxyComputeResult result;
    private final Player receiver;

    public PlayerKnownYsmPacketOutputEvent(YsmPacket packet, ProxyComputeResult result, Player receiver) {
        this.packet = packet;
        this.result = result;
        this.receiver = receiver;
    }

    @NotNull
    public YsmPacket getPacket() {
        return this.packet;
    }

    public void setPacket(@NotNull YsmPacket packet) {
        this.packet = packet;
    }

    @NotNull
    public Player getReceiver() {
        return this.receiver;
    }

    @NotNull
    public ProxyComputeResult getHandleResult() {
        return this.result;
    }

    public void setHandleResult(@NotNull  ProxyComputeResult result) {
        this.result = result;
    }
}
