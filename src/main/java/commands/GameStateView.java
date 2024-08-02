/*
 * Copyright (c) Joseph Prichard 2024.
 */

package commands;

import net.dv8tion.jda.api.EmbedBuilder;
import domain.Tile;
import models.Game;
import models.Player;

import java.awt.image.BufferedImage;

public class GameStateView {

    private static String getScoreText(Game game) {
        return "Black: " + game.blackScore() + " points \n" + "White: " + game.whiteScore() + " points \n";
    }

    private static String getTag(Player player) {
        return player.isBot() ? "" : "<@" + player + ">";
    }

    public static GameView createGameStartView(Game game, BufferedImage image) {
        var desc = "Black: " + game.blackPlayer().name() + "\n " +
            "White: " + game.whitePlayer().name() + "\n " +
            "Use `/view` to view the game and use `/move` to make a move.";
        var embed = new EmbedBuilder()
            .setTitle("Game started!")
            .setDescription(desc);

        return new GameView(embed).setImage(image);
    }

    public static GameView createSimulationStartView(Game game, BufferedImage image) {
        var desc = "Black: " + game.blackPlayer().name() + "\n " +
            "White: " + game.whitePlayer().name();
        var embed = new EmbedBuilder()
            .setTitle("Simulation started!")
            .setDescription(desc);

        return new GameView(embed).setImage(image);
    }

    public static GameView createGameView(Game game, Tile move, BufferedImage image) {
        var desc = getScoreText(game) + "Your opponent has moved: " + move;

        var embed = new EmbedBuilder()
            .setTitle("Your game with " + game.otherPlayer().name())
            .setDescription(desc)
            .setFooter(game.isBlackMove() ? "Black to move" : "White to move");

        return new GameView(embed)
            .setMessage(getTag(game.currentPlayer()))
            .setImage(image);
    }

    public static GameView createSimulationView(Game game, Tile move, BufferedImage image) {
        var desc = getScoreText(game) + game.otherPlayer().name() + " has moved: " + move;

        var embed = new EmbedBuilder()
            .setTitle(game.blackPlayer().name() + " vs " + game.whitePlayer().name())
            .setDescription(desc)
            .setFooter(game.isBlackMove() ? "Black to move" : "White to move");

        return new GameView(embed).setImage(image);
    }

    public static GameView createGameView(Game game, BufferedImage image) {
        var desc = getScoreText(game) + game.currentPlayer().name() + " to move";

        var embed = new EmbedBuilder()
            .setTitle(game.blackPlayer().name() + " vs " + game.whitePlayer().name())
            .setDescription(desc)
            .setFooter(game.isBlackMove() ? "Black to move" : "White to move");

        return new GameView(embed)
            .setMessage(getTag(game.currentPlayer()))
            .setImage(image);
    }

    public static GameView createAnalysisView(Game game, BufferedImage image, long level, Player player) {
        var desc = getScoreText(game);

        var embed = new EmbedBuilder()
            .setTitle("Game Analysis using bot level " + level)
            .setDescription(desc)
            .setFooter("Positive heuristics are better for black, and negative heuristics are better for white");

        return new GameView(embed)
            .setMessage(getTag(player))
            .setImage(image);
    }
}
