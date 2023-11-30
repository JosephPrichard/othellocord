/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Consumer;

public record CommandContext(SlashCommandInteractionEvent event) {
    public String subcommand() {
        return event.getSubcommandName();
    }

    public User getAuthor() {
        return event.getUser();
    }

    public OptionMapping getParam(String key) {
        return Objects.requireNonNull(event.getOption(key));
    }

    @Nullable()
    public OptionMapping getOptionalParam(String key) {
        return event.getOption(key);
    }

    public void reply(String message) {
        event.reply(message).queue();
    }

    public void sendMessage(String message, Consumer<Message> onSuccess) {
        event.getChannel().sendMessage(message).queue(onSuccess);
    }

    public void sendMessage(String message) {
        event.getChannel().sendMessage(message).queue();
    }

    public void deferReply() {
        event.deferReply().queue();
    }

    public void replyEmbeds(MessageEmbed embed) {
        event.replyEmbeds(embed).queue();
    }
}
