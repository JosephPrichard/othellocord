package discord.message.builder;

import modules.player.Player;

public class ChallengeMessageBuilder
{
    private final StringBuilder messageBuilder;
    private Player challenged;
    private Player challenger;

    public ChallengeMessageBuilder() {
        messageBuilder = new StringBuilder();
    }

    public ChallengeMessageBuilder setChallenged(Player challenged) {
        this.challenged = challenged;
        return this;
    }

    public ChallengeMessageBuilder setChallenger(Player challenger) {
        this.challenger = challenger;
        return this;
    }

    public String build() {
        messageBuilder.append("<@")
            .append(challenged.getId())
            .append(">, ")
            .append(challenger.getName())
            .append(" has challenged you to a game of reversi. ")
            .append("Type !accept ")
            .append("<@")
            .append(challenger.getId())
            .append(">, ")
            .append("or ignore to decline.");

        return messageBuilder.toString();
    }
}
