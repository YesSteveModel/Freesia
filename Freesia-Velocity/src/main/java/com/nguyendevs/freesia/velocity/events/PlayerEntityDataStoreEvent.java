package com.nguyendevs.freesia.velocity.events;

import java.util.UUID;

/**
 * çŽ©å®¶æ•°æ®ä¿å­˜æ—¶è§¦å‘è¯¥äº‹ä»¶
 */
public class PlayerEntityDataStoreEvent {
    private final UUID player;
    private byte[] serializedNbtData;

    public PlayerEntityDataStoreEvent(UUID player, byte[] serializedNbtData) {
        this.player = player;
        this.serializedNbtData = serializedNbtData;
    }

    /**
     * èŽ·å–å°†è¦è¢«ä¿å­˜çš„NBTæ•°æ®
     * @return å·²ç¼–ç çš„NBT(unnamed)
     */
    public byte[] getSerializedNbtData() {
        return this.serializedNbtData;
    }

    /**
     * è®¾ç½®æœ€ç»ˆè¿›å…¥datastorageçš„æ•°æ®
     * @param serializedNbtData å·²ç¼–ç çš„NBT(unnamed)
     */
    public void setSerializedNbtData(byte[] serializedNbtData) {
        this.serializedNbtData = serializedNbtData;
    }

    /**
     * èŽ·å–æŒæœ‰è¯¥æ•°æ®çš„çŽ©å®¶çš„UUID
     * @return çŽ©å®¶UUID
     */
    public UUID getPlayer() {
        return this.player;
    }
}

