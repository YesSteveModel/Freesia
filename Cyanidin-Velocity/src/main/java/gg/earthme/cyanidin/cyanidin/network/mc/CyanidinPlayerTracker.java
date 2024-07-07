package gg.earthme.cyanidin.cyanidin.network.mc;

import com.velocitypowered.api.proxy.Player;
import gg.earthme.cyanidin.cyanidin.Cyanidin;

import java.util.*;

public class CyanidinPlayerTracker {
    //TODO Tracker
     public Set<Player> getCanSee(Player target){
         final Set<Player> result = new HashSet<>();

         for (Player player : Cyanidin.PROXY_SERVER.getAllPlayers()){
             if (player != target && Cyanidin.mapperManager.isPlayerInstalledYsm(player)){
                 result.add(player);
             }
         }

         return result;
     }
}
