package gg.earthme.cyanidin.cyanidin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import gg.earthme.cyanidin.cyanidin.Cyanidin;
import gg.earthme.cyanidin.cyanidin.network.backend.MasterServerMessageHandler;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ListYsmPlayersCommand {
    public static void register(){
        final CommandMeta meta = Cyanidin.PROXY_SERVER.getCommandManager()
                .metaBuilder("listysmplayers")
                .plugin(Cyanidin.INSTANCE)
                .build();
        Cyanidin.PROXY_SERVER.getCommandManager().register(meta, create());
    }

    public static @NotNull BrigadierCommand create() {
        LiteralCommandNode<CommandSource> registed = BrigadierCommand.literalArgumentBuilder("listysmplayers")
                .requires(source -> source.hasPermission("cyanidin.commands.listysmplayers"))
                .executes(context -> {
                    final Collection<Player> ysmPlayers = Cyanidin.PROXY_SERVER
                            .getAllPlayers()
                            .stream()
                            .filter(player -> Cyanidin.mapperManager.isPlayerInstalledYsm(player))
                            .toList();

                    Component msg = Cyanidin.languageManager.i18n("cyanidin.list_player_command_header", List.of(), List.of());
                    for (Player player : ysmPlayers) {
                        msg = msg.append(Cyanidin.languageManager.i18n("cyanidin.list_player_command_body", List.of(player.getUsername()), List.of()));
                    }

                    context.getSource().sendMessage(msg);
                    return Command.SINGLE_SUCCESS;
                }).build();

        return new BrigadierCommand(registed);
    }
}
