package discord.message.senders;

import modules.game.GameResult;
import modules.player.Player;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.awt.image.BufferedImage;

public class GameOverMessageSender
{
    private final MessageSender sender = new MessageSender();
    private String resultDesc = "";
    private String messageDesc = "";
    private String scoreDesc = "";

    public GameOverMessageSender setGame(GameResult result) {
        resultDesc = result.getWinner().getName() +
            "'s new rating is " + result.getWinnerElo() +
            " (" + result.formatWinnerDiffElo() + ") \n" +
            result.getLoser().getName() +
            "'s new rating is " + result.getLoserElo() +
            " (" + result.formatLoserDiffElo() + ") \n";
        sender.getEmbedBuilder().setTitle("Game has ended");
        return this;
    }

    public GameOverMessageSender addForfeitMessage(Player winner) {
        messageDesc = winner.getName() + " won by forfeit \n";
        return this;
    }

    public GameOverMessageSender addScoreMessage(int whiteScore, int blackScore) {
        scoreDesc = "Score: " + blackScore + " - " + whiteScore + "\n";
        return this;
    }

    public GameOverMessageSender addMoveMessage(Player winner, String move) {
        messageDesc = winner.getName() + " won with " + move + "\n";
        return this;
    }

    public GameOverMessageSender setTag(GameResult result) {
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

    public GameOverMessageSender setImage(BufferedImage image) {
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
