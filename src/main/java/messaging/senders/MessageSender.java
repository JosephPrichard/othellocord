/*
 * Copyright (c) Joseph Prichard 2023.
 */

package messaging.senders;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import utils.ImageUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

public abstract class MessageSender {

    private final EmbedBuilder embedBuilder;
    private BufferedImage image;
    private String message;

    public MessageSender() {
        embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.GREEN);
    }

    public EmbedBuilder getEmbedBuilder() {
        return embedBuilder;
    }

    public MessageSender setImage(BufferedImage image) {
        this.image = image;
        return this;
    }

    public MessageSender setMessage(String message) {
        this.message = message;
        return this;
    }

    public void sendReply(SlashCommandInteraction event) {
        try {
            var is = ImageUtils.toPngIS(image);
            embedBuilder.setImage("attachment://image.png");

            if (message != null) {
                event.getChannel()
                    .sendMessage(message)
                    .queue();
            }
            event.replyEmbeds(embedBuilder.build())
                .addFile(is, "image.png")
                .queue();
        } catch (IOException ex) {
            event.reply("Unexpected error: couldn't create image").queue();
        }
    }

    public void sendMessage(SlashCommandInteraction event) {
        try {
            var is = ImageUtils.toPngIS(image);
            embedBuilder.setImage("attachment://image.png");

            if (message != null) {
                event.getChannel()
                    .sendMessage(message)
                    .queue();
            }
            event.getChannel().sendMessageEmbeds(embedBuilder.build())
                .addFile(is, "image.png")
                .queue();
        } catch (IOException ex) {
            event.reply("Unexpected error: couldn't create image").queue();
        }
    }
}
