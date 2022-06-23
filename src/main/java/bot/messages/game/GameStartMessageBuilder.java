package bot.messages.game;

import bot.dtos.GameDto;
import bot.messages.MessageBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.awt.image.BufferedImage;

public class GameStartMessageBuilder implements MessageBuilder
{
    private final GameMessageBuilder messageBuilder = new GameMessageBuilder();

    public GameStartMessageBuilder setGame(GameDto game) {
        String desc = "Black: " + game.getBlackPlayer().getName() + "\n " +
            "White: " + game.getWhitePlayer().getName() + "\n " +
            "Use `!view` to view the game and use `!move` to make a move.";
        messageBuilder.getEmbedBuilder()
            .setTitle("Game started!")
            .setDescription(desc);
        return this;
    }

    public GameStartMessageBuilder setTag(GameDto game) {
        messageBuilder.setTag("<@" + game.getBlackPlayer() + ">" + " <@" + game.getWhitePlayer() + ">");
        return this;
    }

    public GameStartMessageBuilder setImage(BufferedImage image) {
        messageBuilder.setImage(image);
        return this;
    }

    public void sendMessage(MessageChannel channel) {
        messageBuilder.sendMessage(channel);
    }
}
