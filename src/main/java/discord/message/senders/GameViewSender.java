/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.message.senders;

import services.game.Game;
import net.dv8tion.jda.api.entities.MessageChannel;
import othello.board.Tile;

import java.awt.image.BufferedImage;

public class GameViewSender
{
    private final MessageSender sender = new MessageSender();

    public GameViewSender setGame(Game game) {
        String desc = "Black: " + game.getBlackScore() + " points \n" +
            "White: " + game.getWhiteScore() + " points \n" +
            game.getCurrentPlayer().getName() + " to move";
        sender.getEmbedBuilder()
            .setTitle(game.getBlackPlayer().getName() + " vs " + game.getWhitePlayer().getName())
            .setDescription(desc);
        return this;
    }

    public GameViewSender setGame(Game game, Tile move) {
        String desc = "Black: " + game.getBlackScore() + "\n" +
            "White: " + game.getWhiteScore() + "\n" +
            "Your opponent has moved: " + move;
        sender.getEmbedBuilder()
            .setTitle("Your game with " + game.getOtherPlayer().getName())
            .setDescription(desc);
        return this;
    }

    public GameViewSender setTag(Game game) {
        if (!game.getCurrentPlayer().isBot()) {
            sender.setMessage("<@" + game.getCurrentPlayer() + ">");
        }
        return this;
    }

    public GameViewSender setImage(BufferedImage image) {
        sender.setImage(image);
        return this;
    }

    public void sendMessage(MessageChannel channel) {
        sender.sendMessage(channel);
    }
}
