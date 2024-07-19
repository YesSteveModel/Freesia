package gg.earthme.cyanidin.cyanidin.network.ysm;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ProxyComputeResult(EnumResult result, ByteBuf data) {

    public ProxyComputeResult(EnumResult result, @Nullable ByteBuf data) {
        this.result = result;
        this.data = data;
    }

    @Override
    @NotNull
    public ByteBuf data() {
        if (this.result != EnumResult.MODIFY) {
            throw new UnsupportedOperationException();
        }

        if (this.data == null) {
            throw new NullPointerException();
        }

        return this.data;
    }

    @Contract(value = "_ -> new", pure = true)
    public static @NotNull ProxyComputeResult ofModify(@NotNull ByteBuf data) {
        return new ProxyComputeResult(EnumResult.MODIFY, data);
    }

    @Contract(value = " -> new", pure = true)
    public static @NotNull ProxyComputeResult ofPass() {
        return new ProxyComputeResult(EnumResult.PASS, null);
    }

    @Contract(value = " -> new", pure = true)
    public static @NotNull ProxyComputeResult ofDrop() {
        return new ProxyComputeResult(EnumResult.DROP, null);
    }

    public enum EnumResult {
        PASS, DROP, MODIFY
    }
}
