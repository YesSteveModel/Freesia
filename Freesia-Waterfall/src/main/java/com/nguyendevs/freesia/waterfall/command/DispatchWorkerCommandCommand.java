package com.nguyendevs.freesia.waterfall.command;

import com.nguyendevs.freesia.waterfall.FreesiaConstants;
import com.nguyendevs.freesia.waterfall.Freesia;
import com.nguyendevs.freesia.waterfall.network.backend.MasterServerMessageHandler;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.ArrayList;
import java.util.List;

public class DispatchWorkerCommandCommand extends Command implements TabExecutor {

    public DispatchWorkerCommandCommand() {
        super("dworkerc", FreesiaConstants.PermissionConstants.DISPATCH_WORKER_COMMAND);
    }

    public static void register() {
        Freesia.PROXY_SERVER.getPluginManager().registerCommand(Freesia.INSTANCE, new DispatchWorkerCommandCommand());
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextComponent.fromLegacyText("Usage: /dworkerc <workerName> <command>"));
            return;
        }

        String workerName = args[0];
        StringBuilder commandBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            commandBuilder.append(args[i]).append(" ");
        }
        String command = commandBuilder.toString().trim();

        MasterServerMessageHandler targetWorkerConnection = null;
        for (MasterServerMessageHandler connection : Freesia.registedWorkers.values()) {
            if (workerName.equals(connection.getWorkerName())) {
                targetWorkerConnection = connection;
                break;
            }
        }

        if (targetWorkerConnection == null) {
            sender.sendMessage(TextComponent.fromLegacyText(LegacyComponentSerializer.legacySection().serialize(
                    Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.WORKER_NOT_FOUND, List.of(),
                            List.of()))));
            return;
        }

        targetWorkerConnection.dispatchCommandToWorker(command, feedback -> {
            if (feedback == null) {
                return;
            }

            sender.sendMessage(TextComponent.fromLegacyText(LegacyComponentSerializer.legacySection().serialize(
                    Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.WORKER_COMMAND_FEEDBACK,
                            List.of("worker_name", "feedback"), List.of(workerName, feedback)))));
        });
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (MasterServerMessageHandler connection : Freesia.registedWorkers.values()) {
                if (connection.getWorkerName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(connection.getWorkerName());
                }
            }
        }
        return completions;
    }
}

