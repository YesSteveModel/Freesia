package com.nguyendevs.freesia.waterfall.events;

import com.google.common.annotations.Beta;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Event;

import com.nguyendevs.freesia.waterfall.network.ysm.YsmState;

/**
 * å½“çŽ©å®¶æ›´æ”¹æ¨¡åž‹æ—¶æˆ–workerè®¾ç½®çŽ©å®¶æ—¶è¯¥äº‹ä»¶ä¼šè¢«è§¦å‘
 * èŽ·å–åˆ°çš„Nbtæ˜¯è¦å‘é€ç»™çŽ©å®¶çš„
 * æ³¨æ„:ä¿®æ”¹è¿‡åŽçš„nbtå¹¶ä¸ä¼šè¢«æŒä¹…åŒ–å³åªä¼šåœ¨å½“å‰è¿›ç¨‹å‘ç”Ÿä½œç”¨è€Œåœ¨é‡å¯åŽå¤±æ•ˆ
 */
@Beta
public class PlayerEntityStateChangeEvent extends Event {
    private final ProxiedPlayer actualPlayer;
    private final int entityId;
    private final YsmState entityState;

    public PlayerEntityStateChangeEvent(ProxiedPlayer actualPlayer, int entityId, YsmState entityState) {
        this.actualPlayer = actualPlayer;
        this.entityId = entityId;
        this.entityState = entityState;
    }

    /**
     * èŽ·å–æŒæœ‰è¿™ä¸ªæ•°æ®çš„çŽ©å®¶
     *
     * @return çŽ©å®¶
     */
    public ProxiedPlayer getPlayer() {
        return this.actualPlayer;
    }

    /**
     * èŽ·å–è¿™ä¸ªçŽ©å®¶åœ¨workerä¾§çš„å®žä½“id
     *
     * @return å®žä½“id
     */
    public int getEntityId() {
        return this.entityId;
    }

    /**
     * èŽ·å–çŽ©å®¶çš„ysmå®žä½“æ•°æ®
     *
     * @return å®žä½“æ•°æ®çš„åŒ…è£…å½¢å¼
     */
    public YsmState getEntityState() {
        return this.entityState;
    }
}

