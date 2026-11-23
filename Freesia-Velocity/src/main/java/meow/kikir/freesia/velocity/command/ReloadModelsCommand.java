package meow.kikir.freesia.velocity.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.FreesiaConstants;
import org.jetbrains.annotations.NotNull;

public class ReloadModelsCommand {
    public static void register() {
        final CommandMeta meta = Freesia.PROXY_SERVER.getCommandManager()
                .metaBuilder("reloadmodels")
                .plugin(Freesia.INSTANCE)
                .build();

        Freesia.PROXY_SERVER.getCommandManager().register(meta, create());
    }

    public static @NotNull BrigadierCommand create() {
        LiteralCommandNode<CommandSource> registed = BrigadierCommand.literalArgumentBuilder("reloadmodels")
                .requires(source -> source.hasPermission(FreesiaConstants.PermissionConstants.RELOAD_MODELS_COMMAND))
                .executes(commandContext -> {
                    Freesia.reloadAllModels();

                    return Command.SINGLE_SUCCESS;
                })
                .build();
        return new BrigadierCommand(registed);
    }
}
