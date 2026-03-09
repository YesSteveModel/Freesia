package com.nguyendevs.freesia.waterfall.command;

import com.nguyendevs.freesia.waterfall.FreesiaConstants;
import com.nguyendevs.freesia.waterfall.Freesia;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ListYsmPlayersCommand extends Command {

    public ListYsmPlayersCommand() {
        super("listysmplayers", FreesiaConstants.PermissionConstants.LIST_PLAYER_COMMAND);
    }

    public static void register() {
        Freesia.PROXY_SERVER.getPluginManager().registerCommand(Freesia.INSTANCE, new ListYsmPlayersCommand());
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        final Collection<ProxiedPlayer> ysmPlayers = Freesia.PROXY_SERVER
                .getPlayers()
                .stream()
                .filter(player -> Freesia.mapperManager.isPlayerInstalledYsm(player))
                .collect(Collectors.toList());

        Component msg = Freesia.languageManager
                .i18n(FreesiaConstants.LanguageConstants.PLAYER_LIST_HEADER, List.of(), List.of()).appendNewline();
        for (ProxiedPlayer player : ysmPlayers) {
            msg = msg
                    .append(Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.PLAYER_LIST_ENTRY,
                            List.of("name"), List.of(player.getName())))
                    .appendNewline();
        }

        sender.sendMessage(TextComponent.fromLegacyText(LegacyComponentSerializer.legacySection().serialize(msg)));
    }
}

