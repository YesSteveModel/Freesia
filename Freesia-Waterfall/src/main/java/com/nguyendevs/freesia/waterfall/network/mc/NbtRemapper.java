package com.nguyendevs.freesia.waterfall.network.mc;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.nguyendevs.freesia.waterfall.utils.FriendlyByteBuf;

import java.io.IOException;

public interface NbtRemapper {
    boolean shouldRemap(int pid);

    byte[] remapToMasterVer(NBTCompound nbt) throws IOException;

    byte[] remapToWorkerVer(NBTCompound nbt) throws IOException;

    NBTCompound readBound(FriendlyByteBuf data) throws IOException;
}

