package meow.kikir.freesia.velocity.events;

import com.velocitypowered.api.proxy.Player;
import meow.kikir.freesia.velocity.network.ysm.ProxyComputeResult;
import meow.kikir.freesia.velocity.network.ysm.protocol.YsmPacket;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 服务器收到来自玩家客户端的ysm数据包时触发该事件
 */
public class PlayerKnownYsmPacketInputEvent {
    @Nullable
    private YsmPacket packet;
    private final Player sender;
    private ProxyComputeResult handleResult;

    public PlayerKnownYsmPacketInputEvent(@Nullable YsmPacket packet, Player sender, ProxyComputeResult handleResult) {
        this.packet = packet;
        this.sender = sender;
        this.handleResult = handleResult;
    }

    public Player getSender() {
        return this.sender;
    }

    @Nullable
    public YsmPacket getPacket() {
        return this.packet;
    }

    public ProxyComputeResult getHandleResult() {
        return this.handleResult;
    }

    public void setHandleResult(ProxyComputeResult handleResult) {
        Objects.requireNonNull(handleResult);

        this.handleResult = handleResult;
    }
}
