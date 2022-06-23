package bot.messages.game;

import bot.dtos.GameDto;
import bot.messages.MessageBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.awt.image.BufferedImage;

public class GameViewMessageBuilder implements MessageBuilder
{
    private final GameMessageBuilder messageBuilder = new GameMessageBuilder();

    public GameViewMessageBuilder setGame(GameDto game) {
        String desc = "Black: " + game.getBlackScore() + " points \n" +
            "White: " + game.getWhiteScore() + " points \n" +
            game.getCurrentPlayer().getName() + " to move";
        messageBuilder.getEmbedBuilder()
            .setTitle(game.getBlackPlayer().getName() + " vs " + game.getWhitePlayer().getName())
            .setDescription(desc);
        return this;
    }

    public GameViewMessageBuilder setGame(GameDto game, String move) {
        String desc = "Black: " + game.getBlackScore() + "\n" +
            "White: " + game.getWhiteScore() + "\n" +
            "Your opponent has moved: " + move;
        messageBuilder.getEmbedBuilder()
            .setTitle("Your game with " + game.getOtherPlayer().getName())
            .setDescription(desc);
        return this;
    }

    public GameViewMessageBuilder setTag(GameDto game) {
        messageBuilder.setTag("<@" + game.getCurrentPlayer() + ">");
        return this;
    }

    public GameViewMessageBuilder setImage(BufferedImage image) {
        messageBuilder.setImage(image);
        return this;
    }

    public void sendMessage(MessageChannel channel) {
        messageBuilder.sendMessage(channel);
    }
}
