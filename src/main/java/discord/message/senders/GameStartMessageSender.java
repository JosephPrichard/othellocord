package discord.message.senders;

import modules.game.Game;

public class GameStartMessageSender extends MessageSender
{
    public GameStartMessageSender setGame(Game game) {
        String desc = "Black: " + game.getBlackPlayer().getName() + "\n " +
            "White: " + game.getWhitePlayer().getName() + "\n " +
            "Use `!view` to view the game and use `!move` to make a move.";
        getEmbedBuilder().setTitle("Game started!").setDescription(desc);
        return this;
    }

    public GameStartMessageSender setTag(Game game) {
        super.setTag("<@" + game.getBlackPlayer() + ">" + " <@" + game.getWhitePlayer() + ">");
        return this;
    }
}
