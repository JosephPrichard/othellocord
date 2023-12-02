/*
 * Copyright (c) Joseph Prichard 2023.
 */

package messaging.senders;

import commands.CommandContext;
import net.dv8tion.jda.api.EmbedBuilder;
import utils.Image;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class MessageSender
{
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

    public void sendReply(CommandContext ctx) {
        var event = ctx.event();
        try {
            var is = Image.toPngIS(image);
            embedBuilder.setImage("attachment://image.png");

            if (message != null) {
                ctx.sendMessage(message);
            }
            event.replyEmbeds(embedBuilder.build())
                .addFile(is, "image.png")
                .queue();
        } catch(IOException ex) {
            event.reply("Unexpected error: couldn't create image").queue();
        }
    }

    public void sendMessage(CommandContext ctx) {
        var event = ctx.event();
        try {
            var is = Image.toPngIS(image);
            embedBuilder.setImage("attachment://image.png");

            if (message != null) {
                ctx.sendMessage(message);
            }
            event.getChannel().sendMessageEmbeds(embedBuilder.build())
                .addFile(is, "image.png")
                .queue();
        } catch(IOException ex) {
            event.reply("Unexpected error: couldn't create image").queue();
        }
    }
}
