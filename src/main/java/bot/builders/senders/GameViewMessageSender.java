package bot.builders.senders;

import bot.dtos.GameDto;
import net.dv8tion.jda.api.entities.MessageChannel;
import othello.board.Tile;

import java.awt.image.BufferedImage;

public class GameViewMessageSender extends MessageSender
{
    public GameViewMessageSender setGame(GameDto game) {
        String desc = "Black: " + game.getBlackScore() + " points \n" +
            "White: " + game.getWhiteScore() + " points \n" +
            game.getCurrentPlayer().getName() + " to move";
        getEmbedBuilder()
            .setTitle(game.getBlackPlayer().getName() + " vs " + game.getWhitePlayer().getName())
            .setDescription(desc);
        return this;
    }

    public GameViewMessageSender setGame(GameDto game, Tile move) {
        String desc = "Black: " + game.getBlackScore() + "\n" +
            "White: " + game.getWhiteScore() + "\n" +
            "Your opponent has moved: " + move;
        getEmbedBuilder()
            .setTitle("Your game with " + game.getOtherPlayer().getName())
            .setDescription(desc);
        return this;
    }

    public GameViewMessageSender setTag(GameDto game) {
        super.setTag("<@" + game.getCurrentPlayer() + ">");
        return this;
    }
}
