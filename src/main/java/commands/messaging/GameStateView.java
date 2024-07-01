/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands.messaging;

import net.dv8tion.jda.api.EmbedBuilder;
import othello.Tile;
import services.game.Game;
import services.player.Player;

import java.awt.*;
import java.awt.image.BufferedImage;

public class GameStateView extends GameView {

    private final EmbedBuilder embed;
    private BufferedImage image;
    private String message;

    public GameStateView(EmbedBuilder embed) {
        this.embed = embed;
        this.embed.setColor(Color.GREEN);
    }

    public GameStateView setImage(BufferedImage image) {
        this.image = image;
        return this;
    }

    public GameStateView setTag(Player player) {
        if (!player.isBot()) {
            message = "<@" + player + ">";
        }
        return this;
    }

    @Override
    BufferedImage getImage() {
        return image;
    }

    @Override
    EmbedBuilder getEmbed() {
        return embed;
    }

    @Override
    String getMessage() {
        return message;
    }

    public static GameStateView createGameStartView(Game game, BufferedImage image) {
        var desc = "Black: " + game.blackPlayer().name() + "\n " +
            "White: " + game.whitePlayer().name() + "\n " +
            "Use `/view` to view the game and use `/move` to make a move.";
        var embed = new EmbedBuilder()
            .setTitle("Game started!")
            .setDescription(desc);

        return new GameStateView(embed).setImage(image);
    }

    public static String getScoreText(Game game) {
        return "Black: " + game.getBlackScore() + " points \n" + "White: " + game.getWhiteScore() + " points \n";
    }

    public static GameStateView createGameView(Game game, Tile move, BufferedImage image) {
        var desc = getScoreText(game) + "Your opponent has moved: " + move;

        var embed = new EmbedBuilder()
            .setTitle("Your game with " + game.getOtherPlayer().name())
            .setDescription(desc)
            .setFooter(game.isBlackMove() ? "Black to move" : "White to move");

        return new GameStateView(embed)
            .setTag(game.getCurrentPlayer())
            .setImage(image);
    }

    public static GameStateView createSimulationView(Game game, Tile move, BufferedImage image) {
        // the other player has already made the move
        var desc = getScoreText(game) + game.getOtherPlayer() + " has moved: " + move;

        var embed = new EmbedBuilder()
            .setTitle(game.blackPlayer().name() + " vs " + game.whitePlayer().name())
            .setDescription(desc)
            .setFooter(game.isBlackMove() ? "Black to move" : "White to move");

        return new GameStateView(embed)
            .setTag(game.getCurrentPlayer())
            .setImage(image);
    }

    public static GameStateView createGameView(Game game, BufferedImage image) {
        var desc = getScoreText(game) + game.getCurrentPlayer().name() + " to move";

        var embed = new EmbedBuilder()
            .setTitle(game.blackPlayer().name() + " vs " + game.whitePlayer().name())
            .setDescription(desc)
            .setFooter(game.isBlackMove() ? "Black to move" : "White to move");

        return new GameStateView(embed)
            .setTag(game.getCurrentPlayer())
            .setImage(image);
    }

    public static GameStateView createAnalysisView(Game game, BufferedImage image, long level) {
        var desc = getScoreText(game);

        var embed = new EmbedBuilder()
            .setTitle("Game Analysis using bot level " + level)
            .setDescription(desc)
            .setFooter("Positive heuristics are better for black, and negative heuristics are better for white");

        return new GameStateView(embed)
            .setTag(game.getCurrentPlayer())
            .setImage(image);
    }
}
