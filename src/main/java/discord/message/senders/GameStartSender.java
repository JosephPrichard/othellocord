/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.message.senders;

import services.game.Game;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.awt.image.BufferedImage;

public class GameStartSender
{
    private final MessageSender sender = new MessageSender();

    public GameStartSender setGame(Game game) {
        String desc = "Black: " + game.getBlackPlayer().getName() + "\n " +
            "White: " + game.getWhitePlayer().getName() + "\n " +
            "Use `!view` to view the game and use `!move` to make a move.";
        sender.getEmbedBuilder().setTitle("Game started!").setDescription(desc);
        return this;
    }

    public GameStartSender setTag(Game game) {
        sender.setMessage("<@" + game.getBlackPlayer() + ">" + " <@" + game.getWhitePlayer() + ">");
        return this;
    }

    public GameStartSender setImage(BufferedImage image) {
        sender.setImage(image);
        return this;
    }

    public void sendMessage(MessageChannel channel) {
        sender.sendMessage(channel);
    }
}
