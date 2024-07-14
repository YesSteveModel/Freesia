package gg.earthme.cyanidin.cyanidinworker.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At(value = "HEAD"), cancellable = true)
    public void sendPacketPInject(Packet<?> packet, CallbackInfo ci){
        if (this.checkPacket(packet)){
            ci.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At(value = "HEAD"), cancellable = true)
    public void sendPacketPCInject(Packet<?> packet, PacketSendListener packetSendListener, CallbackInfo ci){
        if (this.checkPacket(packet)){
            if (packetSendListener != null){
                packetSendListener.onSuccess();
            }
            ci.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V", at = @At(value = "HEAD"), cancellable = true)
    public void sendPacketPCBInject(Packet<?> packet, PacketSendListener packetSendListener, boolean bl, CallbackInfo ci){
        if (this.checkPacket(packet)){
            if (packetSendListener != null){
                packetSendListener.onSuccess();
            }
            ci.cancel();
        }
    }

    @Unique
    public boolean checkPacket(Packet<?> pkt) {
        return pkt instanceof ClientboundSetTimePacket
                || pkt instanceof ClientboundAddEntityPacket
                || pkt instanceof ClientboundEntityEventPacket
                || pkt instanceof ClientboundSetEntityMotionPacket
                || pkt instanceof ClientboundMoveEntityPacket
                || pkt instanceof ClientboundSoundEntityPacket
                || pkt instanceof ClientboundRegistryDataPacket
                || pkt instanceof ClientboundChangeDifficultyPacket
                || pkt instanceof ClientboundPlayerAbilitiesPacket
                || pkt instanceof ClientboundUpdateTagsPacket
                || pkt instanceof ClientboundUpdateRecipesPacket
                || pkt instanceof ClientboundSetHealthPacket
                || pkt instanceof ClientboundLevelChunkWithLightPacket
                || pkt instanceof ClientboundSetExperiencePacket
                || pkt instanceof ClientboundChunkBatchStartPacket
                || pkt instanceof ClientboundSetChunkCacheCenterPacket
                || pkt instanceof ClientboundContainerSetDataPacket;
    }
}
