/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.message;

import services.Game;
import othello.Tile;

public class GameViewSender extends MessageSender
{
    public GameViewSender setGame(Game game) {
        String desc = "Black: " + game.getBlackScore() + " points \n" +
            "White: " + game.getWhiteScore() + " points \n" +
            game.getCurrentPlayer().getName() + " to move";
       getEmbedBuilder()
            .setTitle(game.getBlackPlayer().getName() + " vs " + game.getWhitePlayer().getName())
            .setDescription(desc);
        return this;
    }

    public GameViewSender setGame(Game game, Tile move) {
        String desc = "Black: " + game.getBlackScore() + "\n" +
            "White: " + game.getWhiteScore() + "\n" +
            "Your opponent has moved: " + move;
       getEmbedBuilder()
            .setTitle("Your game with " + game.getOtherPlayer().getName())
            .setDescription(desc);
        return this;
    }

    public GameViewSender setTag(Game game) {
        if (!game.getCurrentPlayer().isBot()) {
            setMessage("<@" + game.getCurrentPlayer() + ">");
        }
        return this;
    }
}
