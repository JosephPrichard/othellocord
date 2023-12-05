/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands.messaging;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import services.game.GameResult;
import services.player.Player;
import services.stats.StatsResult;

public class GameOverSender extends MessageSender {

    private String resultDesc = "";
    private String messageDesc = "";
    private String scoreDesc = "";

    public GameOverSender() {
        super(new EmbedBuilder());
    }

    public GameOverSender setResults(GameResult gameRes, StatsResult statsRes) {
        resultDesc = gameRes.winner().name() +
            "'s new rating is " + statsRes.winnerElo() +
            " (" + statsRes.formatWinnerEloDiff() + ") \n" +
            gameRes.loser().name() +
            "'s new rating is " + statsRes.loserElo() +
            " (" + statsRes.formatLoserEloDiff() + ") \n";
        embed.setTitle("Game has ended");
        return this;
    }

    public GameOverSender addForfeitMessage(Player winner) {
        messageDesc = winner.name() + " won by forfeit \n";
        return this;
    }

    public GameOverSender addScoreMessage(int whiteScore, int blackScore) {
        scoreDesc = "Score: " + blackScore + " - " + whiteScore + "\n";
        return this;
    }

    public GameOverSender addMoveMessage(Player winner, String move) {
        messageDesc = winner.name() + " won with " + move + "\n";
        return this;
    }

    public GameOverSender setTag(GameResult result) {
        if (!result.winner().isBot()) {
            message = "<@" + result.winner() + "> ";
        }
        if (!result.loser().isBot()) {
            message = "<@" + result.loser() + "> ";
        }
        return this;
    }

    @Override()
    public void sendReply(SlashCommandInteraction interaction) {
        var desc = messageDesc;
        if (!scoreDesc.isEmpty()) {
            desc += scoreDesc;
        }
        desc += "\n" + resultDesc;
        embed.setDescription(desc);
        super.sendReply(interaction);
    }
}
