package gg.earthme.cyanidin.cyanidin.network.mc;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import gg.earthme.cyanidin.cyanidin.utils.FriendlyByteBuf;

import java.io.IOException;

public interface NbtRemapper {
    boolean shouldRemap(int pid);

    byte[] remapToMasterVer(NBTCompound nbt) throws IOException;

    byte[] remapToWorkerVer(NBTCompound nbt) throws IOException;

    NBTCompound readBound(FriendlyByteBuf data) throws IOException;
}
