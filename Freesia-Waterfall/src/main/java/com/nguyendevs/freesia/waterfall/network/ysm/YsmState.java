package com.nguyendevs.freesia.waterfall.network.ysm;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class YsmState {
    private final NBTCompound nbtState;
    private final byte[] binaryState;
    private final boolean isBinary;

    private YsmState(NBTCompound nbtState, byte[] binaryState, boolean isBinary) {
        this.nbtState = nbtState;
        this.binaryState = binaryState;
        this.isBinary = isBinary;
    }

    public static YsmState ofNbt(@NotNull NBTCompound nbt) {
        return new YsmState(nbt, null, false);
    }

    public static YsmState ofBinary(@NotNull byte[] bytes) {
        return new YsmState(null, bytes, true);
    }

    public boolean isBinary() {
        return isBinary;
    }

    @Nullable
    public NBTCompound getNbt() {
        return nbtState;
    }

    @Nullable
    public byte[] getBinary() {
        return binaryState;
    }
}

