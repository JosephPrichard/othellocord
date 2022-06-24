package bot.builders.senders;

import bot.dtos.GameDto;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.awt.image.BufferedImage;

public class GameStartMessageSender extends MessageSender
{
    public GameStartMessageSender setGame(GameDto game) {
        String desc = "Black: " + game.getBlackPlayer().getName() + "\n " +
            "White: " + game.getWhitePlayer().getName() + "\n " +
            "Use `!view` to view the game and use `!move` to make a move.";
        getEmbedBuilder().setTitle("Game started!").setDescription(desc);
        return this;
    }

    public GameStartMessageSender setTag(GameDto game) {
        super.setTag("<@" + game.getBlackPlayer() + ">" + " <@" + game.getWhitePlayer() + ">");
        return this;
    }
}
