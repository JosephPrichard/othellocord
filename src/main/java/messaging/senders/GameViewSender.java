/*
 * Copyright (c) Joseph Prichard 2023.
 */

package messaging.senders;

import othello.Tile;
import services.game.Game;

public class GameViewSender extends MessageSender {

    public GameViewSender setGame(Game game) {
        var desc = "Black: " + game.getBlackScore() + " points \n" +
            "White: " + game.getWhiteScore() + " points \n" +
            game.getCurrentPlayer().name() + " to move";
        getEmbedBuilder()
            .setTitle(game.blackPlayer().name() + " vs " + game.whitePlayer().name())
            .setDescription(desc);
        return this;
    }

    public GameViewSender setGame(Game game, Tile move) {
        var desc = "Black: " + game.getBlackScore() + "\n" +
            "White: " + game.getWhiteScore() + "\n" +
            "Your opponent has moved: " + move;
        getEmbedBuilder()
            .setTitle("Your game with " + game.getOtherPlayer().name())
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
