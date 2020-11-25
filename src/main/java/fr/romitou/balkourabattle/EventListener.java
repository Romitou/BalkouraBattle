package fr.romitou.balkourabattle;

import fr.romitou.balkourabattle.tasks.CheckParticipantMatch;
import fr.romitou.balkourabattle.utils.ParticipantCheckType;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EventListener implements Listener {

    public void playerConnect (PlayerJoinEvent event) {
        if (BattleHandler.getPlayers().containsValue(event.getPlayer()))
            new CheckParticipantMatch(event.getPlayer(), ParticipantCheckType.CONNECTED);
    }

    public void playerDisconnect (PlayerQuitEvent event) {
        if (BattleHandler.getPlayers().containsValue(event.getPlayer()))
            new CheckParticipantMatch(event.getPlayer(), ParticipantCheckType.DISCONNECTED);
    }

}
