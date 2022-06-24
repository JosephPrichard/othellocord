package bot.builders.message;

import bot.dtos.PlayerDto;

public class ChallengeMessageBuilder
{
    private final StringBuilder messageBuilder;
    private PlayerDto challenged;
    private PlayerDto challenger;

    public ChallengeMessageBuilder() {
        messageBuilder = new StringBuilder();
    }

    public ChallengeMessageBuilder setChallenged(PlayerDto challenged) {
        this.challenged = challenged;
        return this;
    }

    public ChallengeMessageBuilder setChallenger(PlayerDto challenger) {
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
