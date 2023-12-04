/*
 * Copyright (c) Joseph Prichard 2023.
 */

package messaging.builders;

import services.player.Player;

public class ChallengeBuilder {

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
            .append(challenged.id())
            .append(">, ")
            .append(challenger.name())
            .append(" has challenged you to a game of reversi. ")
            .append("Type !accept ")
            .append("<@")
            .append(challenger.id())
            .append(">, ")
            .append("or ignore to decline.");

        return messageBuilder.toString();
    }
}
