/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.commands;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class Command
{
    private final Logger logger = Logger.getLogger("command.handler");
    private final String key;
    private final SlashCommandData data;

    public Command(String key, SlashCommandData data) {
        this.data = data;
        this.key = key;
    }

    public Command(String key, String desc) {
        this(key, desc, new OptionData[0]);
    }

    public Command(String key, String desc, OptionData... options) {
        this(key, Commands.slash(key, desc).addOptions(options));
    }

    public Command(String key, String desc, SubcommandData... subCommands) {
        this(key, Commands.slash(key, desc).addSubcommands(subCommands));
    }

    public String getKey() {
        return key;
    }

    public SlashCommandData getData() {
        return data;
    }

    abstract protected void doCommand(CommandContext ctx);

    public final void onMessageEvent(SlashCommandInteractionEvent event) {
        var ctx = new CommandContext(event);
        try {
            doCommand(ctx);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Fatal error during command", ex);
            ctx.reply("An unexpected error has occurred.");
        }
    }

    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        // default implementation is a no-op, we assume a command has no autocomplete
    }
}
