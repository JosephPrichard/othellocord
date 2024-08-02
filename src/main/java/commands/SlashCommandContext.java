/*
 * Copyright (c) Joseph Prichard 2024.
 */

package commands;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import models.Player;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    public void sendView(String message) {
        event.getChannel().sendMessage(message).queue();
    }

    public void replyEmbeds(MessageEmbed embed) {
        event.replyEmbeds(embed).queue();
    }

    private static InputStream toPngInputStream(BufferedImage image) throws IOException {
        var os = new ByteArrayOutputStream();
        ImageIO.write(image, "png", os);
        return new ByteArrayInputStream(os.toByteArray());
    }

    public void replyView(GameView view) {
        replyView(view, (hook) -> {
        });
    }

    public void replyView(GameView view, Consumer<InteractionHook> onSuccess) {
        try {
            var embed = view.getEmbed();
            var message = view.getMessage();

            var is = toPngInputStream(view.getImage());
            embed.setImage("attachment://image.png");

            if (!message.isEmpty()) {
                event.getChannel().sendMessage(message).queue();
            }

            event.replyEmbeds(embed.build())
                .addFile(is, "image.png")
                .queue(onSuccess);
        } catch (IOException ex) {
            event.reply("Unexpected error: couldn't create image").queue();
        }
    }

    public void sendView(GameView view) {
        try {
            var embed = view.getEmbed();
            var message = view.getMessage();

            var is = toPngInputStream(view.getImage());
            embed.setImage("attachment://image.png");

            if (!message.isEmpty()) {
                event.getChannel().sendMessage(message).queue();
            }

            event.getChannel()
                .sendMessageEmbeds(embed.build())
                .addFile(is, "image.png")
                .queue();
        } catch (IOException ex) {
            event.reply("Unexpected error: couldn't create image").queue();
        }
    }
}
