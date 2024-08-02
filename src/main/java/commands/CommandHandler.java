/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction;

public abstract class CommandHandler {

    public abstract void onCommand(CommandContext ctx);

    public void onAutoComplete(CommandAutoCompleteInteraction interaction) {
        // default implementation is a no-op, we assume a command has no autocomplete
    }
}
