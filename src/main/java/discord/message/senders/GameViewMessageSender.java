package discord.message.senders;

import modules.game.Game;
import othello.board.Tile;

public class GameViewMessageSender extends MessageSender
{
    public GameViewMessageSender setGame(Game game) {
        String desc = "Black: " + game.getBlackScore() + " points \n" +
            "White: " + game.getWhiteScore() + " points \n" +
            game.getCurrentPlayer().getName() + " to move";
        getEmbedBuilder()
            .setTitle(game.getBlackPlayer().getName() + " vs " + game.getWhitePlayer().getName())
            .setDescription(desc);
        return this;
    }

    public GameViewMessageSender setGame(Game game, Tile move) {
        String desc = "Black: " + game.getBlackScore() + "\n" +
            "White: " + game.getWhiteScore() + "\n" +
            "Your opponent has moved: " + move;
        getEmbedBuilder()
            .setTitle("Your game with " + game.getOtherPlayer().getName())
            .setDescription(desc);
        return this;
    }

    public GameViewMessageSender setTag(Game game) {
        super.setTag("<@" + game.getCurrentPlayer() + ">");
        return this;
    }
}
