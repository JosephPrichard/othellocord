package bot.messages;

import net.dv8tion.jda.api.entities.MessageChannel;

public interface MessageBuilder
{
    void sendMessage(MessageChannel channel);
}
