/*
 * Copyright (c) Joseph Prichard 2024.
 */

package commands;

import models.Stats;
import net.dv8tion.jda.api.EmbedBuilder;
import domain.Tile;
import models.Game;
import models.Player;

import java.awt.image.BufferedImage;

public class GameResultView {

    private static String getStatsMessage(Game.Result gameRes, Stats.Result statsRes) {
        return gameRes.winner().name() +
            "'s new rating is " + statsRes.winnerElo() +
            " (" + statsRes.formatWinnerEloDiff() + ") \n" +
            gameRes.loser().name() +
            "'s new rating is " + statsRes.loserElo() +
            " (" + statsRes.formatLoserEloDiff() + ") \n";
    }

    private static String getForfeitMessage(Player winner) {
        return winner.name() + " won by forfeit \n";
    }

    private static String getScoreMessage(int whiteScore, int blackScore) {
        return "Score: " + blackScore + " - " + whiteScore + "\n";
    }

    private static String getMoveMessage(Player winner, String move) {
        return winner.name() + " won with " + move + "\n";
    }

    private static String getTag(Game.Result result) {
        var message = "";
        if (!result.winner().isBot()) {
            message = "<@" + result.winner() + "> ";
        } else if (!result.loser().isBot()) {
            message = "<@" + result.loser() + "> ";
        }
        return message;
    }

    public static GameView createGameOverView(Game.Result result, Stats.Result statsResult, Tile move, Game game, BufferedImage image) {
        var embed = new EmbedBuilder();

        var desc = getMoveMessage(result.winner(), move.toString()) +
            getScoreMessage(game.whiteScore(), game.blackScore()) + "\n" +
            getStatsMessage(result, statsResult);
        embed.setDescription(desc);

        return new GameView(embed)
            .setTitle("Game has ended")
            .setMessage(getTag(result))
            .setImage(image);
    }

    public static GameView createForfeitView(Game.Result result, Stats.Result statsResult, BufferedImage image) {
        var embed = new EmbedBuilder();

        var desc = getForfeitMessage(result.winner()) + "\n" +
            getStatsMessage(result, statsResult);
        embed.setDescription(desc);

        return new GameView(embed)
            .setTitle("Game has ended")
            .setMessage(getTag(result))
            .setImage(image);
    }

    public static GameView createSimulationView(Game game, Tile move, BufferedImage image) {
        var result = game.createResult();

        var embed = new EmbedBuilder();

        var desc = getMoveMessage(result.winner(), move.toString()) +
            getScoreMessage(game.whiteScore(), game.blackScore());
        embed.setDescription(desc);

        return new GameView(embed)
            .setTitle("Simulation has ended")
            .setMessage(getTag(result))
            .setImage(image);
    }
}
