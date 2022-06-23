package bot.messages.help;

import bot.commands.info.CommandParam;
import bot.messages.MessageBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.awt.*;
import java.util.List;

public class HelpMessageBuilder implements MessageBuilder
{
    private static final int GREEN = new Color(82, 172, 85).getRGB();

    private final EmbedBuilder embedBuilder;
    private String commandKey;
    private List<CommandParam> params;
    private String description;

    public HelpMessageBuilder() {
        embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(GREEN);
    }

    public HelpMessageBuilder setCommandKey(String commandKey) {
        this.commandKey = commandKey;
        return this;
    }

    public HelpMessageBuilder setParams(List<CommandParam> params) {
        this.params = params;
        return this;
    }

    public HelpMessageBuilder setDescription(String description) {
        this.description = description;
        return this;
    }

    public void sendMessage(MessageChannel channel) {
        StringBuilder builder = new StringBuilder();
        builder.append("!").append(commandKey).append(" ");
        for (CommandParam param : params) {
            builder.append("`").append(param.getName()).append("` ");
        }

        embedBuilder.setTitle("Help for command \"" + commandKey +"\"")
            .addField("Description:", description, false)
            .addField("Usage:", builder.toString(), false);

        for (CommandParam param : params) {
            embedBuilder.addField(param.getName(), param.getDesc(), false);
        }
        channel.sendMessageEmbeds(embedBuilder.build()).queue();
    }
}
