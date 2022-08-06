package discord.message.senders;

import modules.game.GameResult;
import modules.Player;
import net.dv8tion.jda.api.entities.MessageChannel;

public class GameOverMessageSender extends MessageSender
{
    private String resultDesc = "";
    private String messageDesc = "";
    private String scoreDesc = "";

    public GameOverMessageSender setGame(GameResult result) {
        resultDesc = result.getWinner().getName() +
            "'s new rating is " + result.getWinnerElo() +
            " (" + result.formatWinnerDiffElo() + ") \n" +
            result.getLoser().getName() +
            "'s new rating is " + result.getLoserElo() +
            " (" + result.formatLoserDiffElo() + ") \n";
        getEmbedBuilder().setTitle("Game has ended");
        return this;
    }

    public GameOverMessageSender addForfeitMessage(Player winner) {
        messageDesc = winner.getName() + " won by forfeit \n";
        return this;
    }

    public GameOverMessageSender addScoreMessage(int whiteScore, int blackScore) {
        scoreDesc = "Score: " + blackScore + " - " + whiteScore + "\n";
        return this;
    }

    public GameOverMessageSender addMoveMessage(Player winner, String move) {
        messageDesc = winner.getName() + " won with " + move + "\n";
        return this;
    }

    public GameOverMessageSender setTag(GameResult result) {
        String tag = "";
        if (!result.getWinner().isBot()) {
            tag += "<@" + result.getWinner() + "> ";
        }
        if (!result.getLoser().isBot()) {
            tag += "<@" + result.getLoser() + "> ";
        }
        super.setTag(tag);
        return this;
    }

    @Override
    public void sendMessage(MessageChannel channel) {
        String desc = messageDesc;
        if (!scoreDesc.equals("")) {
            desc += scoreDesc;
        }
        desc += "\n" + resultDesc;
        getEmbedBuilder().setDescription(desc);
        super.sendMessage(channel);
    }
}
