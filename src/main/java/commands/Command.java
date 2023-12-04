/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction;

public abstract class Command {

    private final String key;

    public Command(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public abstract void onCommand(CommandContext ctx);

    public void onAutoComplete(CommandAutoCompleteInteraction interaction) {
        // default implementation is a no-op, we assume a command has no autocomplete
    }
}
