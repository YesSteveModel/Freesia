package gg.earthme.cyanidin.cyanidinworker.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.configuration.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At(value = "HEAD"), cancellable = true)
    public void sendPacketPInject(Packet<?> packet, CallbackInfo ci){
        if (!this.checkPacket(packet)){
            ci.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At(value = "HEAD"), cancellable = true)
    public void sendPacketPCInject(Packet<?> packet, PacketSendListener packetSendListener, CallbackInfo ci){
        if (!this.checkPacket(packet)){
            if (packetSendListener != null){
                packetSendListener.onSuccess();
            }
            ci.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V", at = @At(value = "HEAD"), cancellable = true)
    public void sendPacketPCBInject(Packet<?> packet, PacketSendListener packetSendListener, boolean bl, CallbackInfo ci){
        if (!this.checkPacket(packet)){
            if (packetSendListener != null){
                packetSendListener.onSuccess();
            }
            ci.cancel();
        }
    }

    @Unique
    public boolean checkPacket(Packet<?> pkt) {
        return pkt instanceof ClientboundLoginCompressionPacket
                || pkt instanceof ClientboundHelloPacket
                || pkt instanceof ClientboundGameProfilePacket
                || pkt instanceof ClientboundCustomPayloadPacket
                || pkt instanceof ClientboundPingPacket
                || pkt instanceof ClientboundFinishConfigurationPacket
                || pkt instanceof ClientboundLoginPacket
                || pkt instanceof ClientboundLoginDisconnectPacket
                || pkt instanceof ClientboundStartConfigurationPacket
                || pkt instanceof ClientboundRegistryDataPacket
                || pkt instanceof ClientboundUpdateEnabledFeaturesPacket
                || pkt instanceof ClientboundSelectKnownPacks
                || pkt instanceof ClientboundUpdateTagsPacket
                || pkt instanceof ClientboundResetChatPacket
                || pkt instanceof ClientboundKeepAlivePacket;
    }
}
