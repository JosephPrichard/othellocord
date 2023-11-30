/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.message;

import services.Game;

public class GameStartSender extends MessageSender
{
    public GameStartSender setGame(Game game) {
        var desc = "Black: " + game.getBlackPlayer().getName() + "\n " +
            "White: " + game.getWhitePlayer().getName() + "\n " +
            "Use `/view` to view the game and use `/move` to make a move.";
        getEmbedBuilder().setTitle("Game started!").setDescription(desc);
        return this;
    }

    public GameStartSender setTag(Game game) {
        setMessage("<@" + game.getBlackPlayer() + ">" + " <@" + game.getWhitePlayer() + ">");
        return this;
    }
}
