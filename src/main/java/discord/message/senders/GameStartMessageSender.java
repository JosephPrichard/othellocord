package discord.message.senders;

import modules.game.Game;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.awt.image.BufferedImage;

public class GameStartMessageSender
{
    private final MessageSender sender = new MessageSender();

    public GameStartMessageSender setGame(Game game) {
        String desc = "Black: " + game.getBlackPlayer().getName() + "\n " +
            "White: " + game.getWhitePlayer().getName() + "\n " +
            "Use `!view` to view the game and use `!move` to make a move.";
        sender.getEmbedBuilder().setTitle("Game started!").setDescription(desc);
        return this;
    }

    public GameStartMessageSender setTag(Game game) {
        sender.setMessage("<@" + game.getBlackPlayer() + ">" + " <@" + game.getWhitePlayer() + ">");
        return this;
    }

    public GameStartMessageSender setImage(BufferedImage image) {
        sender.setImage(image);
        return this;
    }

    public void sendMessage(MessageChannel channel) {
        sender.sendMessage(channel);
    }
}
