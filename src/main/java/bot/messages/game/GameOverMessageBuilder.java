package bot.messages.game;

import bot.dtos.GameResultDto;
import bot.dtos.PlayerDto;
import bot.messages.MessageBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.awt.image.BufferedImage;

public class GameOverMessageBuilder implements MessageBuilder
{
    private final GameMessageBuilder messageBuilder = new GameMessageBuilder();
    private String resultDesc = "";
    private String messageDesc = "";

    public GameOverMessageBuilder setGame(GameResultDto result) {
        resultDesc = result.getWinner().getName() +
            "'s new rating is " + result.getWinnerElo() +
            " (" + result.formatWinnerDiffElo() + ") \n" +
            result.getLoser().getName() +
            "'s new rating is " + result.getLoserElo() +
            " (" + result.formatLoserDiffElo() + ") \n";
        messageBuilder.getEmbedBuilder()
            .setTitle("Game has ended");
        return this;
    }

    public GameOverMessageBuilder addForfeitMessage(PlayerDto winner) {
        messageDesc = winner.getName() + " won by forfeit \n";
        return this;
    }

    public GameOverMessageBuilder addMoveMessage(PlayerDto winner, String move) {
        messageDesc = winner.getName() + " won with " + move + "\n";
        return this;
    }

    public GameOverMessageBuilder setTag(GameResultDto result) {
        messageBuilder.setTag("<@" + result.getWinner() + "> " + "<@" + result.getLoser() + ">");
        return this;
    }

    public GameOverMessageBuilder setImage(BufferedImage image) {
        messageBuilder.setImage(image);
        return this;
    }

    public void sendMessage(MessageChannel channel) {
        messageBuilder.getEmbedBuilder()
            .setDescription(messageDesc + "\n" + resultDesc);
        messageBuilder.sendMessage(channel);
    }
}
