package com.nguyendevs.freesia.velocity.events;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.google.common.annotations.Beta;
import com.velocitypowered.api.proxy.Player;

/**
 * å½“çŽ©å®¶æ›´æ”¹æ¨¡åž‹æ—¶æˆ–workerè®¾ç½®çŽ©å®¶æ—¶è¯¥äº‹ä»¶ä¼šè¢«è§¦å‘
 * èŽ·å–åˆ°çš„Nbtæ˜¯è¦å‘é€ç»™çŽ©å®¶çš„
 * æ³¨æ„:ä¿®æ”¹è¿‡åŽçš„nbtå¹¶ä¸ä¼šè¢«æŒä¹…åŒ–å³åªä¼šåœ¨å½“å‰è¿›ç¨‹å‘ç”Ÿä½œç”¨è€Œåœ¨é‡å¯åŽå¤±æ•ˆ
 */
@Beta
public class PlayerEntityStateChangeEvent {
    private final Player actualPlayer;
    private final int entityId;
    private final NBTCompound entityState;

    public PlayerEntityStateChangeEvent(Player actualPlayer, int entityId, NBTCompound entityState) {
        this.actualPlayer = actualPlayer;
        this.entityId = entityId;
        this.entityState = entityState;
    }

    /**
     * èŽ·å–æŒæœ‰è¿™ä¸ªæ•°æ®çš„çŽ©å®¶
     *
     * @return çŽ©å®¶
     */
    public Player getPlayer() {
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
     * @return å®žä½“æ•°æ®çš„nbtå½¢å¼
     */
    public NBTCompound getEntityState() {
        return this.entityState;
    }
}

