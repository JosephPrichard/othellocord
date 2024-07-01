/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands.messaging;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import othello.Tile;
import services.game.Game;
import services.game.GameResult;
import services.player.Player;
import services.stats.StatsResult;

import java.awt.*;
import java.awt.image.BufferedImage;
public class GameResultView extends GameView {

    private final EmbedBuilder embed;
    private BufferedImage image;
    private String message;
    private String resultDesc = "";
    private String messageDesc = "";
    private String scoreDesc = "";

    public GameResultView() {
        this.embed = new EmbedBuilder();
        this.embed.setColor(Color.GREEN);
    }

    public GameResultView setTitle(String title) {
        embed.setTitle(title);
        return this;
    }

    public GameResultView setStats(GameResult gameRes, StatsResult statsRes) {
        resultDesc = gameRes.winner().name() +
            "'s new rating is " + statsRes.winnerElo() +
            " (" + statsRes.formatWinnerEloDiff() + ") \n" +
            gameRes.loser().name() +
            "'s new rating is " + statsRes.loserElo() +
            " (" + statsRes.formatLoserEloDiff() + ") \n";
        return this;
    }

    public GameResultView addForfeitMessage(Player winner) {
        messageDesc = winner.name() + " won by forfeit \n";
        return this;
    }

    public GameResultView addScoreMessage(int whiteScore, int blackScore) {
        scoreDesc = "Score: " + blackScore + " - " + whiteScore + "\n";
        return this;
    }

    public GameResultView addMoveMessage(Player winner, String move) {
        messageDesc = winner.name() + " won with " + move + "\n";
        return this;
    }

    public GameResultView setTag(GameResult result) {
        if (!result.winner().isBot()) {
            message = "<@" + result.winner() + "> ";
        }
        if (!result.loser().isBot()) {
            message = "<@" + result.loser() + "> ";
        }
        return this;
    }

    public GameResultView setImage(BufferedImage image) {
        this.image = image;
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

    private void setDescription() {
        var desc = messageDesc;
        if (!scoreDesc.isEmpty()) {
            desc += scoreDesc;
        }
        desc += "\n" + resultDesc;
        embed.setDescription(desc);
    }

    public void sendReply(SlashCommandInteraction event) {
        setDescription();
        super.sendReply(event);
    }

    public void sendMessage(SlashCommandInteraction event) {
        setDescription();
        super.sendMessage(event);
    }

    public static GameResultView createGameOverView(GameResult result, StatsResult statsResult, Tile move, Game game, BufferedImage image) {
        return new GameResultView()
            .setTitle("Game has ended")
            .setStats(result, statsResult)
            .addMoveMessage(result.winner(), move.toString())
            .addScoreMessage(game.getWhiteScore(), game.getBlackScore())
            .setTag(result)
            .setImage(image);
    }

    public static GameResultView createForfeitView(GameResult result, StatsResult statsResult, BufferedImage image) {
        return new GameResultView()
            .setTitle("Game has ended")
            .setStats(result, statsResult)
            .addForfeitMessage(result.winner())
            .setTag(result)
            .setImage(image);
    }

    public static GameResultView createSimulationView(GameResult result, Tile move, Game game, BufferedImage image) {
        return new GameResultView()
            .setTitle("Simulation has ended")
            .addMoveMessage(result.winner(), move.toString())
            .addScoreMessage(game.getWhiteScore(), game.getBlackScore())
            .setImage(image);
    }
}
