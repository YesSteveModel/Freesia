package meow.kikir.freesia.velocity.network.mc.impl;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTLimiter;
import com.github.retrooper.packetevents.protocol.nbt.serializer.DefaultNBTSerializer;
import io.netty.buffer.ByteBufInputStream;
import meow.kikir.freesia.velocity.network.mc.NbtRemapper;
import meow.kikir.freesia.velocity.utils.FriendlyByteBuf;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class StandardNbtRemapperImpl implements NbtRemapper {
    private final DefaultNBTSerializer serializer = new DefaultNBTSerializer();

    @Override
    public boolean shouldRemap(int pid) {
        return pid < 764; //1.20.2
    }

    @Override
    public byte[] remapToMasterVer(NBTCompound nbt) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(bos);

        this.serializer.serializeTag(dos, nbt, true); //1.20.2
        dos.flush();

        return bos.toByteArray();
    }

    @Override
    public byte[] remapToWorkerVer(NBTCompound nbt) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(bos);

        this.serializer.serializeTag(dos, nbt, false);
        dos.flush();

        return bos.toByteArray();
    }

    @Override
    public NBTCompound readBound(FriendlyByteBuf data) throws IOException {
        return (NBTCompound) this.serializer.deserializeTag(NBTLimiter.forBuffer(data, Integer.MAX_VALUE), new ByteBufInputStream(data), false); //1.21
    }
}
