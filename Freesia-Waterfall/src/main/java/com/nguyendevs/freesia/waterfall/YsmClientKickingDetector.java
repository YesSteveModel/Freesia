package com.nguyendevs.freesia.waterfall;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class YsmClientKickingDetector implements Runnable {
    private final Map<ProxiedPlayer, Long> lastNotDetected = new ConcurrentHashMap<>();
    private final long timeOut;
    private volatile boolean scheduleNext = true;
    private volatile ScheduledTask lastScheduled = null;

    public YsmClientKickingDetector() {
        this.timeOut = FreesiaConfig.ysmDetectionTimeout * 1000L * 1000L;
    }

    public void onPlayerJoin(ProxiedPlayer player) {
        this.lastNotDetected.put(player, System.nanoTime());
    }

    public void onPlayerLeft(ProxiedPlayer player) {
        this.lastNotDetected.remove(player);
    }

    public void signalStop() {
        this.scheduleNext = false;

        if (this.lastScheduled != null) {
            this.lastScheduled.cancel();
        }
    }

    public void bootstrap() {
        this.lastScheduled = Freesia.PROXY_SERVER.getScheduler().schedule(Freesia.INSTANCE, this, 50,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        try {
            final Set<ProxiedPlayer> toKickOrCleanUp = new HashSet<>();

            for (Map.Entry<ProxiedPlayer, Long> entry : this.lastNotDetected.entrySet()) {
                final long joinTimeNanos = entry.getValue();
                final ProxiedPlayer target = entry.getKey();

                if (!target.isConnected()) {
                    toKickOrCleanUp.add(target);
                    continue;
                }

                if (System.nanoTime() - joinTimeNanos > this.timeOut) {
                    if (!FreesiaConfig.kickIfYsmNotInstalled || Freesia.mapperManager.isPlayerInstalledYsm(target)) {
                        continue;
                    }

                    toKickOrCleanUp.add(target);
                }
            }

            for (ProxiedPlayer target : toKickOrCleanUp) {
                this.lastNotDetected.remove(target);

                if (target.isConnected()) {
                    target.disconnect(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                                    .serialize(Freesia.languageManager.i18n(
                                            FreesiaConstants.LanguageConstants.HANDSHAKE_TIMED_OUT, List.of(),
                                            List.of()))));
                }
            }
        } finally {
            if (this.scheduleNext) {
                this.lastScheduled = Freesia.PROXY_SERVER.getScheduler().schedule(Freesia.INSTANCE, this, 50,
                        TimeUnit.MILLISECONDS);
            }
        }
    }
}

