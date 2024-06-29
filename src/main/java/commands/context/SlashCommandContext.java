/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands.context;

import commands.messaging.MessageSender;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import services.player.Player;

import java.util.function.Consumer;

public record SlashCommandContext(SlashCommandInteraction event) implements CommandContext {

    public String subcommand() {
        return event.getSubcommandName();
    }

    public User getUser() {
        return event.getUser();
    }


    public Player getPlayer() {
        return new Player(getUser());
    }

    public OptionMapping getParam(String key) {
        return event.getOption(key);
    }

    public Long getLongParam(String key) {
        var opt = event.getOption(key);
        return opt != null ? opt.getAsLong() : null;
    }

    public Player getPlayerParam(String key) {
        var opt = event.getOption(key);
        return opt != null ? new Player(opt.getAsUser()) : null;
    }

    public String getStringParam(String key) {
        var opt = event.getOption(key);
        return opt != null ? opt.getAsString() : null;
    }

    public void reply(String message) {
        event.reply(message).queue();
    }

    public void reply(String message, Consumer<InteractionHook> onSuccess) {
        event.reply(message).queue(onSuccess);
    }

    public void sendMessage(String message) {
        event.getChannel().sendMessage(message).queue();
    }

    public void replyEmbeds(MessageEmbed embed) {
        event.replyEmbeds(embed).queue();
    }

    public void sendReply(MessageSender sender) {
        sender.sendReply(event);
    }

    public void sendMessage(MessageSender sender) {
        sender.sendMessage(event);
    }
}
