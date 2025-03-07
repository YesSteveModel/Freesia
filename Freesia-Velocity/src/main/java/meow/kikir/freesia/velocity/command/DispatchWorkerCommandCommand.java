package meow.kikir.freesia.velocity.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import meow.kikir.freesia.velocity.FreesiaConstants;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.network.backend.MasterServerMessageHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DispatchWorkerCommandCommand {
    public static void register() {
        final CommandMeta meta = Freesia.PROXY_SERVER.getCommandManager()
                .metaBuilder("dworkerc")
                .plugin(Freesia.INSTANCE)
                .build();

        Freesia.PROXY_SERVER.getCommandManager().register(meta, create());
    }

    public static @NotNull BrigadierCommand create() {
        LiteralCommandNode<CommandSource> registed = BrigadierCommand.literalArgumentBuilder("dworkerc")
                .requires(source -> source.hasPermission(FreesiaConstants.PermissionConstants.DISPATCH_WORKER_COMMAND))
                .then(
                        BrigadierCommand.requiredArgumentBuilder("workerName", StringArgumentType.word()).suggests((ctx, builder) -> {
                                    for (MasterServerMessageHandler connection : Freesia.registedWorkers.values()) {
                                        builder.suggest(connection.getWorkerName());
                                    }
                                    return builder.buildFuture();
                                })
                                .then(
                                        BrigadierCommand.requiredArgumentBuilder("mcCommand", StringArgumentType.word())
                                                .executes(context -> {
                                                    final CommandSource source = context.getSource();
                                                    final String workerName = StringArgumentType.getString(context, "workerName");
                                                    final String command = StringArgumentType.getString(context, "mcCommand");

                                                    MasterServerMessageHandler targetWorkerConnection = null;
                                                    for (MasterServerMessageHandler connection : Freesia.registedWorkers.values()) {
                                                        if (workerName.equals(connection.getWorkerName())) {
                                                            targetWorkerConnection = connection;
                                                            break;
                                                        }
                                                    }

                                                    if (targetWorkerConnection == null) {
                                                        source.sendMessage(Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.WORKER_NOT_FOUND, List.of(), List.of()));
                                                        return -1;
                                                    }

                                                    targetWorkerConnection.dispatchCommandToWorker(command, feedback -> {
                                                        if (feedback == null) {
                                                            return;
                                                        }

                                                        source.sendMessage(Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.WORKER_COMMAND_FEEDBACK, List.of("worker_name", "feedback"), List.of(workerName, feedback)));
                                                    });
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                )


                ).build();
        return new BrigadierCommand(registed);
    }

}