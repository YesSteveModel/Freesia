package meow.kikir.freesia.velocity.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import meow.kikir.freesia.velocity.Freesia;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class ListYsmPlayersCommand {
    public static void register() {
        final CommandMeta meta = Freesia.PROXY_SERVER.getCommandManager()
                .metaBuilder("listysmplayers")
                .plugin(Freesia.INSTANCE)
                .build();
        Freesia.PROXY_SERVER.getCommandManager().register(meta, create());
    }

    public static @NotNull BrigadierCommand create() {
        LiteralCommandNode<CommandSource> registed = BrigadierCommand.literalArgumentBuilder("listysmplayers")
                .requires(source -> source.hasPermission("cyanidin.commands.listysmplayers"))
                .executes(context -> {
                    final Collection<Player> ysmPlayers = Freesia.PROXY_SERVER
                            .getAllPlayers()
                            .stream()
                            .filter(player -> Freesia.mapperManager.isPlayerInstalledYsm(player))
                            .toList();

                    Component msg = Freesia.languageManager.i18n("cyanidin.list_player_command_header", List.of(), List.of()).appendNewline();
                    for (Player player : ysmPlayers) {
                        msg = msg
                                .append(Freesia.languageManager.i18n("cyanidin.list_player_command_body", List.of("name"), List.of(player.getUsername())))
                                .appendNewline();
                    }

                    context.getSource().sendMessage(msg);
                    return Command.SINGLE_SUCCESS;
                }).build();

        return new BrigadierCommand(registed);
    }
}
