package bot.builders.senders;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import bot.utils.ImageUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class MessageSender
{
    private final EmbedBuilder embedBuilder;
    private BufferedImage image;
    private String tag;

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

    public MessageSender setTag(String tag) {
        this.tag = tag;
        return this;
    }

    public void sendMessage(MessageChannel channel) {
        try {
            InputStream is = ImageUtils.toPngIS(image);
            embedBuilder.setImage("attachment://image.png");

            if (tag != null) {
                channel.sendMessage(tag)
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
