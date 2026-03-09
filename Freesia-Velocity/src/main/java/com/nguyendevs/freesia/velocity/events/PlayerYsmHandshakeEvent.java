package com.nguyendevs.freesia.velocity.events;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.proxy.Player;

/**
 * YsmçŽ©å®¶çš„æ¡æ‰‹äº‹ä»¶
 * æ³¨æ„: é˜»å¡žäº‹ä»¶
 */
public class PlayerYsmHandshakeEvent implements ResultedEvent<ResultedEvent.GenericResult> {
    private final Player player;
    private GenericResult result = GenericResult.allowed();

    public PlayerYsmHandshakeEvent(Player player) {
        this.player = player;
    }

    /**
     * èŽ·å–å½“å‰çš„çŽ©å®¶
     *
     * @return çŽ©å®¶
     */
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public GenericResult getResult() {
        return this.result;
    }

    @Override
    public void setResult(GenericResult result) {
        this.result = result;
    }
}

