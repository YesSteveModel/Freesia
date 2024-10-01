package gg.earthme.cyanidin.cyanidinbackend;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public class Utils {
    public static Player randomPlayerIfNotFound(UUID uuid) {
        Player expected = Bukkit.getPlayer(uuid);

        if (expected != null) {
            return expected;
        }

        final Optional<? extends Player> any =  Bukkit.getOnlinePlayers().stream().findAny();

        return any.orElse(null);
    }
}
