/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.message;

import services.game.Game;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.awt.image.BufferedImage;

public class GameStartSender extends MessageSender
{
    public GameStartSender setGame(Game game) {
        String desc = "Black: " + game.getBlackPlayer().getName() + "\n " +
            "White: " + game.getWhitePlayer().getName() + "\n " +
            "Use `!view` to view the game and use `!move` to make a move.";
        getEmbedBuilder().setTitle("Game started!").setDescription(desc);
        return this;
    }

    public GameStartSender setTag(Game game) {
        setMessage("<@" + game.getBlackPlayer() + ">" + " <@" + game.getWhitePlayer() + ">");
        return this;
    }
}
