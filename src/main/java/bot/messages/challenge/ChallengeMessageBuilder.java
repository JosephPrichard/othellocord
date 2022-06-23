package bot.messages.challenge;

import bot.dtos.PlayerDto;
import bot.messages.MessageBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;

public class ChallengeMessageBuilder implements MessageBuilder
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

    public void sendMessage(MessageChannel channel) {
        messageBuilder.append("<@").append(challenged.getId()).append(">, ")
            .append(challenger.getName()).append(" has challenged you to a game of reversi. ")
            .append("Type !accept ")
            .append("<@").append(challenger.getId()).append(">, ")
            .append("or ignore to decline.");

        channel.sendMessage(messageBuilder.toString()).queue();
    }
}
