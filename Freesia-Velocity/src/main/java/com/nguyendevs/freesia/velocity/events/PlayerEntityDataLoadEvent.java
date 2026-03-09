package com.nguyendevs.freesia.velocity.events;

import java.util.UUID;

/**
 * åŠ è½½çŽ©å®¶æ•°æ®æ—¶ä¼šè§¦å‘è¯¥äº‹ä»¶
 */
public class PlayerEntityDataLoadEvent {
    private final UUID player;
    private byte[] serializedNbtData;

    public PlayerEntityDataLoadEvent(UUID player, byte[] serializedNbtData) {
        this.player = player;
        this.serializedNbtData = serializedNbtData;
    }

    /**
     * èŽ·å–è¢«åŠ è½½çŽ©å®¶çš„UUID
     * æ³¨æ„: åœ¨æ­¤äº‹ä»¶è§¦å‘æ—¶ï¼ŒæœåŠ¡ç«¯å¯èƒ½ä»åœ¨å¤„ç†ServerConnectedEvent,æ‰€ä»¥æ­¤æ—¶æ— æ³•é€šè¿‡è¯¥UUIDèŽ·å–åˆ°velocityä¸Šçš„Playerå¯¹è±¡
     * @see com.velocitypowered.api.event.player.ServerConnectedEvent
     * @return è¢«åŠ è½½çŽ©å®¶çš„UUID
     */
    public UUID getPlayer() {
        return this.player;
    }

    /**
     * èŽ·å–æœªè§£ç çš„NBTæ•°æ®(unnamed)
     * @return
     */
    public byte[] getSerializedNbtData() {
        return this.serializedNbtData;
    }

    /**
     *è®¾ç½®æœ€ç»ˆçš„NBTæ•°æ®
     * @param serializedNbtData å·²ç»ç¼–ç çš„UUIDæ•°æ®(unnamed)
     */
    public void setSerializedNbtData(byte[] serializedNbtData) {
        this.serializedNbtData = serializedNbtData;
    }
}

