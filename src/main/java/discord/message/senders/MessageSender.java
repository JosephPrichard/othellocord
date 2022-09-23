package discord.message.senders;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import utils.ImageUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

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

    public void sendMessage(MessageChannel channel) {
        try {
            InputStream is = ImageUtils.toPngIS(image);
            embedBuilder.setImage("attachment://image.png");

            if (message != null) {
                channel.sendMessage(message)
                    .setEmbeds(embedBuilder.build())
                    .addFile(is, "image.png")
                    .queue();
            } else {
                channel.sendMessageEmbeds(embedBuilder.build())
                    .addFile(is, "image.png")
                    .queue();
            }
        } catch(IOException ex) {
            channel.sendMessage("Unexpected error: couldn't create image").queue();
        }
    }
}
