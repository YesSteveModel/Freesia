package gg.earthme.cyanidin.cyanidinworker.mixin;

import com.elfmcys.yesstevemodel.OoO0oo0oOOOOoooO0o00Oooo;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.file.Path;

/*
 *临时Mixin
 * 用来修复ysm开发组犯的一个智障错误
 * (System.load传相对目录导致崩服)
 */
@Mixin(value = OoO0oo0oOOOOoooO0o00Oooo.class, remap = false)
public class YsmNativeLibLoaderMixin {
    @Redirect(method = "O00ooo0ooooO0000OO0ooooo", at = @At(value = "INVOKE", target = "Ljava/nio/file/Path;toString()Ljava/lang/String;"))
    private static @NotNull String redirectToString(Path path) {
        if (path.isAbsolute()){
            return path.toString();
        }

        return path.toAbsolutePath().toString();
    }
}
