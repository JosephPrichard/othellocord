package bot.builders.senders;

import bot.dtos.GameResultDto;
import bot.dtos.PlayerDto;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.awt.image.BufferedImage;

public class GameOverMessageSender extends MessageSender
{
    private String resultDesc = "";
    private String messageDesc = "";

    public GameOverMessageSender setGame(GameResultDto result) {
        resultDesc = result.getWinner().getName() +
            "'s new rating is " + result.getWinnerElo() +
            " (" + result.formatWinnerDiffElo() + ") \n" +
            result.getLoser().getName() +
            "'s new rating is " + result.getLoserElo() +
            " (" + result.formatLoserDiffElo() + ") \n";
        getEmbedBuilder().setTitle("Game has ended");
        return this;
    }

    public GameOverMessageSender addForfeitMessage(PlayerDto winner) {
        messageDesc = winner.getName() + " won by forfeit \n";
        return this;
    }

    public GameOverMessageSender addMoveMessage(PlayerDto winner, String move) {
        messageDesc = winner.getName() + " won with " + move + "\n";
        return this;
    }

    public GameOverMessageSender setTag(GameResultDto result) {
        String tag = "";
        if (!result.getWinner().isBot()) {
            tag += "<@" + result.getWinner() + "> ";
        }
        if (!result.getLoser().isBot()) {
            tag += "<@" + result.getLoser() + "> ";
        }
        super.setTag(tag);
        return this;
    }

    @Override
    public void sendMessage(MessageChannel channel) {
        getEmbedBuilder().setDescription(messageDesc + "\n" + resultDesc);
        super.sendMessage(channel);
    }
}
