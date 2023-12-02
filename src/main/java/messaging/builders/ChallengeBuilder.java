/*
 * Copyright (c) Joseph Prichard 2023.
 */

package messaging.builders;

import services.game.Player;

public class ChallengeBuilder
{
    private final StringBuilder messageBuilder;
    private Player challenged;
    private Player challenger;

    public ChallengeBuilder() {
        messageBuilder = new StringBuilder();
    }

    public ChallengeBuilder setChallenged(Player challenged) {
        this.challenged = challenged;
        return this;
    }

    public ChallengeBuilder setChallenger(Player challenger) {
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
