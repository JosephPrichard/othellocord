/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.message.senders;

import services.game.GameResult;
import services.player.Player;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.awt.image.BufferedImage;

public class GameOverSender
{
    private final MessageSender sender = new MessageSender();
    private String resultDesc = "";
    private String messageDesc = "";
    private String scoreDesc = "";

    public GameOverSender setGame(GameResult result) {
        resultDesc = result.getWinner().getName() +
            "'s new rating is " + result.getWinnerElo() +
            " (" + result.formatWinnerDiffElo() + ") \n" +
            result.getLoser().getName() +
            "'s new rating is " + result.getLoserElo() +
            " (" + result.formatLoserDiffElo() + ") \n";
        sender.getEmbedBuilder().setTitle("Game has ended");
        return this;
    }

    public GameOverSender addForfeitMessage(Player winner) {
        messageDesc = winner.getName() + " won by forfeit \n";
        return this;
    }

    public GameOverSender addScoreMessage(int whiteScore, int blackScore) {
        scoreDesc = "Score: " + blackScore + " - " + whiteScore + "\n";
        return this;
    }

    public GameOverSender addMoveMessage(Player winner, String move) {
        messageDesc = winner.getName() + " won with " + move + "\n";
        return this;
    }

    public GameOverSender setTag(GameResult result) {
        String tag = "";
        if (!result.getWinner().isBot()) {
            tag += "<@" + result.getWinner() + "> ";
        }
        if (!result.getLoser().isBot()) {
            tag += "<@" + result.getLoser() + "> ";
        }
        sender.setMessage(tag);
        return this;
    }

    public GameOverSender setImage(BufferedImage image) {
        sender.setImage(image);
        return this;
    }

    public void sendMessage(MessageChannel channel) {
        String desc = messageDesc;
        if (!scoreDesc.equals("")) {
            desc += scoreDesc;
        }
        desc += "\n" + resultDesc;
        sender.getEmbedBuilder().setDescription(desc);
        sender.sendMessage(channel);
    }
}
