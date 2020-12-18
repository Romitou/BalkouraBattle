package fr.romitou.balkourabattle;

import at.stefangeyer.challonge.model.Match;
import at.stefangeyer.challonge.model.Participant;
import fr.romitou.balkourabattle.elements.Arena;
import fr.romitou.balkourabattle.elements.ArenaStatus;
import fr.romitou.balkourabattle.elements.MatchScore;
import fr.romitou.balkourabattle.tasks.MatchScoreUpdatingTask;
import fr.romitou.balkourabattle.tasks.MatchStartingTask;
import fr.romitou.balkourabattle.tasks.MatchStoppingTask;
import fr.romitou.balkourabattle.tasks.ParticipantDisconnectionTimerTask;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class BattleHandler {

    public static void handleEndRound(Match match, long loserId) {
        MatchScore matchScore = new MatchScore(match.getScoresCsv());

        // Update scores for the current set and the match's scores.
        matchScore.setWinnerSet(matchScore.getCurrentRound(), !(match.getPlayer1Id().equals(loserId)));
        matchScore.addRound();
        match.setScoresCsv(matchScore.getScoreCsv());

        if ((matchScore.getCurrentRound() >= 2
                && matchScore.getWinSets(true) != matchScore.getWinSets(false))
                || matchScore.getCurrentRound() >= 3) {
            new MatchStoppingTask(match).runTaskAsynchronously(BalkouraBattle.getInstance());
            return;
        }

        // Send them to Challonge for a live update.
        new MatchScoreUpdatingTask(match, false).runTaskAsynchronously(BalkouraBattle.getInstance());

        Arena arena = BattleManager.getArenaByMatchId(match.getId());
        if (arena == null) {
            // TODO
            return;
        }

        // Renewing the match as the score requirements are not meet.
        new MatchStartingTask(match, arena).runTaskAsynchronously(BalkouraBattle.getInstance());
    }

    public static void handleDeath(Player player) {
        if (!BattleManager.containsName(player.getName())) return;
        Participant participant = BattleManager.getParticipant(player.getUniqueId());
        if (participant == null) return;
        Match match = BattleManager.getCurrentMatchByPlayerId(participant.getId());
        if (match == null) {
            // TODO: alert
            return;
        }
        List<OfflinePlayer> offlinePlayers = BattleManager.getPlayers(match);
        if (offlinePlayers == null) {
            // TODO: alert
            return;
        }

        // Something went wrong as there's no two players. Someone probably disconnected. Abort.
        if (!(offlinePlayers.stream().filter(offlinePlayer -> offlinePlayer.getPlayer() != null).count() == 2)) {
            // TODO: alert
            return;
        }

        BattleManager.stopTimer(match);
        handleEndRound(match, participant.getId());
    }

    public static void handleJoin(Player player) {
        Participant participant = BattleManager.getParticipant(player.getUniqueId());
        if (participant == null) {
            if (BattleManager.isTournamentStarted()) {
                ChatManager.sendMessage(player, "Le tournois a déjà commencé ! Vous êtes spectateur.");
                player.setGameMode(GameMode.SPECTATOR);
            } else {
                ChatManager.sendMessage(player, "Bienvenue, le tournois va commencer dans quelques instants.");
                player.setGameMode(GameMode.ADVENTURE);
            }
        } else {
            Match match = BattleManager.getCurrentMatchByPlayerId(participant.getId());
            if (match == null) return;
            handleConnectWhileFighting(match, player);
        }
    }

    public static void handleDisconnect(Player player) {
        System.out.println("Handle disconnect for " + player.getName());
        Participant participant = BattleManager.getParticipant(player.getUniqueId());
        if (participant == null) return;
        Match match = BattleManager.getCurrentMatchByPlayerId(participant.getId());
        if (match == null) return;
        handleDisconnectWhileFighting(match);
    }

    public static void handleConnectWhileFighting(Match match, Player player) {
        BattleManager.stopDisconnectionTimer(match);
        List<OfflinePlayer> offlinePlayers = BattleManager.getPlayers(match);
        if (offlinePlayers == null) {
            // TODO: alert
            return;
        }
        Arena arena = BattleManager.getArenaByMatchId(match.getId());
        if (arena == null || arena.getArenaStatus() != ArenaStatus.BUSY) {
            // TODO: alert
            return;
        }
        offlinePlayers.stream()
                .filter(offlinePlayer -> offlinePlayer.getPlayer() != null)
                .forEach(offlinePlayer -> ChatManager.sendMessage(
                        offlinePlayer.getPlayer(),
                        offlinePlayer.getPlayer().equals(player)
                                ? "Ouf, vous revoilà. Reprise de votre match dans quelques instants, préparez-vous ! " +
                                "Attention, une seconde déconnexion résultera de la victoire instantanée de votre " +
                                "adversaire."
                                : "Votre adversaire s'est reconnecté ! Préparez-vous à la reprise de votre match ..."
                ));
        new MatchStartingTask(match, arena).runTaskLaterAsynchronously(BalkouraBattle.getInstance(), 100);
    }

    public static void handleDisconnectWhileFighting(Match match) {
        BattleManager.stopTimer(match);
        Arena arena = BattleManager.getArenaByMatchId(match.getId());
        if (arena == null || arena.getArenaStatus() != ArenaStatus.BUSY) {
            // TODO: alert
            return;
        }
        List<OfflinePlayer> offlinePlayers = BattleManager.getPlayers(match);
        if (offlinePlayers == null) {
            // TODO: alert
            return;
        }
        OfflinePlayer disconnectedPlayer = offlinePlayers.stream()
                .filter(offlinePlayer -> offlinePlayer.getPlayer() == null)
                .findFirst()
                .orElse(null);
        if (disconnectedPlayer == null) {
            // TODO: alert
            return;
        }
        offlinePlayers.stream()
                .filter(offlinePlayer -> offlinePlayer.getPlayer() != null)
                .forEach(offlinePlayer -> ChatManager.sendMessage(
                        offlinePlayer.getPlayer(),
                        "Votre adversaire s'est déconnecté. Celui-ci doit se reconnecter dans la minute, " +
                                "ou vous serez désigné comme vainqueur de cette manche. Si celui-ci se déconnecte " +
                                "à nouveau, vous serez instantanément gagnant de ce match."
                ));
        BukkitTask bukkitTask = new ParticipantDisconnectionTimerTask(match, 60)
                .runTaskTimerAsynchronously(BalkouraBattle.getInstance(), 0, 20);
        BattleManager.disconnections.put(match, bukkitTask.getTaskId());
        int disconnections = BattleManager.playerDisconnections.getOrDefault(disconnectedPlayer, 0);
        disconnections++;
        BattleManager.playerDisconnections.put(disconnectedPlayer, disconnections);
    }

    public static void handleDisconnectionTimerEnd(Match match, List<OfflinePlayer> offlinePlayers) {
        OfflinePlayer disconnectedPlayer = offlinePlayers.stream()
                .filter(offlinePlayer -> offlinePlayer.getPlayer() == null)
                .findFirst()
                .orElse(null);
        if (disconnectedPlayer == null) {
            // TODO: alert
            return;
        }
        int disconnectionCount = BattleManager.playerDisconnections.getOrDefault(disconnectedPlayer, 1);
        Participant participant = BattleManager.getParticipant(disconnectedPlayer.getUniqueId());
        if (participant == null) {
            // TODO: alert
            return;
        }
        if (disconnectionCount == 1)
            handleEndRound(match, participant.getId());
        else if (disconnectionCount >= 2)
            new MatchStoppingTask(match).runTaskAsynchronously(BalkouraBattle.getInstance());
    }

}
