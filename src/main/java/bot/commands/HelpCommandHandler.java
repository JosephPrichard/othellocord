package bot.commands;

import bot.commands.info.CommandsInfo;
import bot.commands.abstracts.CommandContext;
import bot.commands.abstracts.CommandHandler;
import bot.commands.info.CommandInfo;
import bot.messages.help.HelpListMessageBuilder;
import bot.messages.help.HelpMessageBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.logging.Logger;

public class HelpCommandHandler extends CommandHandler
{
    private final Logger logger = Logger.getLogger("command.help");
    private final CommandsInfo commandsInfo;

    public HelpCommandHandler(CommandsInfo commandsInfo) {
        super(
            "Displays help for any command",
            0,
            "command"
        );
        this.commandsInfo = commandsInfo;
    }

    @Override
    protected void doCommand(CommandContext ctx) {
        MessageReceivedEvent event = ctx.getEvent();
        MessageChannel channel = event.getChannel();

        String command = ctx.getParam("command");

        // check if a specific command help is specified
        if (command == null) {
            // show help for all bot.commands
            HelpListMessageBuilder messageBuilder = new HelpListMessageBuilder();
            for (CommandInfo info : commandsInfo.getCommandInfo()) {
                messageBuilder.addCommand(info.getKey(), info.getParams());
            }
            messageBuilder.sendMessage(channel);
        } else {
            // shows help for a specific command
            CommandInfo info = commandsInfo.getCommandInfo()
                .stream().filter((c) -> c.getKey().equals(command)).findFirst().orElse(null);

            if (info == null) {
                channel.sendMessage("No such command exists!").queue();
                return;
            }

            new HelpMessageBuilder()
                .setCommandKey(info.getKey())
                .setDescription(info.getDescription())
                .setParams(info.getParams())
                .sendMessage(channel);
        }

        logger.info("Used help command.");
    }
}
