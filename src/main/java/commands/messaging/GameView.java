/*
 * Copyright (c) Joseph Prichard 2024.
 */

package commands.messaging;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public abstract class GameView {

    abstract BufferedImage getImage();

    abstract EmbedBuilder getEmbed();

    abstract String getMessage();

    public void sendReply(SlashCommandInteraction event) {
        try {
            var embed = getEmbed();
            var message = getMessage();

            var is = toPngInputStream(getImage());
            embed.setImage("attachment://image.png");

            if (message != null) {
                event.getChannel()
                    .sendMessage(message)
                    .queue();
            }
            event.replyEmbeds(embed.build())
                .addFile(is, "image.png")
                .queue();
        } catch (IOException ex) {
            event.reply("Unexpected error: couldn't create image").queue();
        }
    }

    public void sendMessage(SlashCommandInteraction event) {
        try {
            var embed = getEmbed();
            var message = getMessage();

            var is = toPngInputStream(getImage());
            embed.setImage("attachment://image.png");

            if (message != null) {
                event.getChannel()
                    .sendMessage(message)
                    .queue();
            }
            event.getChannel().sendMessageEmbeds(embed.build())
                .addFile(is, "image.png")
                .queue();
        } catch (IOException ex) {
            event.reply("Unexpected error: couldn't create image").queue();
        }
    }

    public void editMessageUsingHook(InteractionHook hook) {
        try {
            var embed = getEmbed();
            var message = getMessage();

            var is = toPngInputStream(getImage());
            embed.setImage("attachment://image.png");

            hook.editOriginalEmbeds(embed.build())
                .addFile(is, "image.png")
                .queue();
            if (message != null) {
                hook.editOriginal(getMessage()).queue();
            }
        } catch (IOException ex) {
            hook.editOriginal("Unexpected error: couldn't create image").queue();
        }
    }

    static InputStream toPngInputStream(BufferedImage image) throws IOException {
        var os = new ByteArrayOutputStream();
        ImageIO.write(image, "png", os);
        return new ByteArrayInputStream(os.toByteArray());
    }
}
