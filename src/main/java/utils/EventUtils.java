/*
 * Copyright (c) Joseph Prichard 2024.
 */

package utils;

import discord.GameView;
import models.Player;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;

public class EventUtils {

    public static Long getLongParam(SlashCommandInteraction event, String key) {
        OptionMapping opt = event.getOption(key);
        return opt != null ? opt.getAsLong() : null;
    }

    public static Player getPlayerParam(SlashCommandInteraction event, String key) {
        OptionMapping opt = event.getOption(key);
        return opt != null ? new Player(opt.getAsUser()) : null;
    }

    public static String getStringParam(SlashCommandInteraction event, String key) {
        OptionMapping opt = event.getOption(key);
        return opt != null ? opt.getAsString() : null;
    }

    public static void replyView(SlashCommandInteraction event, GameView view) {
        replyView(event, view, (hook) -> {
        });
    }

    public static void replyView(SlashCommandInteraction event, GameView view, Consumer<InteractionHook> onSuccess) {
        try {
            EmbedBuilder embed = view.getEmbed();
            String message = view.getMessage();

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(view.getImage(), "png", os);
            ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());

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

    public static void sendView(SlashCommandInteraction event, GameView view) {
        try {
            EmbedBuilder embed = view.getEmbed();
            String message = view.getMessage();

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(view.getImage(), "png", os);
            ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());

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
