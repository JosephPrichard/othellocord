package bot.commands.abstracts;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class Command
{
    private final Logger logger = Logger.getLogger("command.handler");
    private final String key;
    private final String desc;
    private final List<String> params;
    private final int minCount;

    public Command(String key, String desc) {
        this(key, desc, new String[0]);
    }

    public Command(String key, String desc, String... params) {
        this(key, desc, params.length, params);
    }

    public Command(String key, String desc, int minCount, String... params) {
        this.params = Arrays.stream(params).toList();
        this.minCount = minCount;
        this.key = key;
        this.desc = desc;
    }

    public String getKey() {
        return key;
    }

    public String getDesc() {
        return desc;
    }

    public List<String> getParams() {
        return params;
    }

    abstract protected void doCommand(CommandContext ctx);

    private void sendErrorResponse(MessageChannel channel) {
        channel.sendMessage("Error processing command. Type !help command for details.").queue();
    }

    protected boolean isContextValid(CommandContext ctx) {
        int i = 0;
        for (String param : params) {
            if (ctx.getParam(param) == null && i < minCount) {
                return false;
            }
            i++;
        }
        return true;
    }

    public final void onMessageEvent(MessageReceivedEvent event) {
        CommandContext ctx = new CommandContext();

        String[] chunks = event.getMessage().getContentRaw().split("\\s+");

        // parse the chunks from the bot.commands into the ctx variables
        ctx.setEvent(event);
        ctx.setKey(chunks[0]);

        // for each chunk, check the params list to get the name of the param and add it to params ctx
        for (int i = 1; i < chunks.length && i < params.size() + 1; i++) {
            String param = params.get(i - 1);
            if (param != null) {
                ctx.addParam(param, chunks[i]);
            } else {
                sendErrorResponse(ctx.getEvent().getChannel());
                return;
            }
        }

        if (isContextValid(ctx)) {
            try {
                doCommand(ctx);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Fatal error during command", ex);
                ctx.getEvent().getChannel().sendMessage("An unexpected error has occurred.").queue();
            }
        } else {
            sendErrorResponse(ctx.getEvent().getChannel());
        }
    }
}
