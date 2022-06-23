package bot.messages.help;

import bot.commands.info.CommandParam;
import bot.messages.MessageBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.awt.*;
import java.util.List;

public class HelpListMessageBuilder implements MessageBuilder
{
    private static final int GREEN = new Color(82, 172, 85).getRGB();

    private final EmbedBuilder embedBuilder;

    public HelpListMessageBuilder() {
        embedBuilder = new EmbedBuilder()
            .setColor(GREEN)
            .setTitle("Help")
            .setDescription("List of bot commands. Type !help [command] for more information about a certain command.");
    }

    public void addCommand(String command, List<CommandParam> params) {
        StringBuilder builder = new StringBuilder();
        if (params.size() > 0) {
            builder.append("Arguments: ");
            for (CommandParam param : params) {
                builder.append("`").append(param.getName()).append("` ");
            }
        } else {
            builder.append("No arguments");
        }
        embedBuilder.addField(command, builder.toString(), false);
    }

    public void sendMessage(MessageChannel channel) {
        channel.sendMessageEmbeds(embedBuilder.build()).queue();
    }
}
