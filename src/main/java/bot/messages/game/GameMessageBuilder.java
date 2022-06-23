package bot.messages.game;

import bot.messages.MessageBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import bot.utils.ImageUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class GameMessageBuilder implements MessageBuilder
{
    private static final int GREEN = new Color(82, 172, 85).getRGB();

    private final EmbedBuilder embedBuilder;
    private BufferedImage image;
    private String tag;

    public GameMessageBuilder() {
        embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(GREEN);
    }

    public static int getColor() {
        return GREEN;
    }

    public EmbedBuilder getEmbedBuilder() {
        return embedBuilder;
    }

    public BufferedImage getImage() {
        return image;
    }

    public void setImage(BufferedImage image) {
        this.image = image;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public void sendMessage(MessageChannel channel) {
        try {
            InputStream is = ImageUtils.toPngIS(image);
            embedBuilder.setImage("attachment://board.png");

            if (tag != null) {
                channel.sendMessage(tag)
                    .setEmbeds(embedBuilder.build())
                    .addFile(is, "board.png")
                    .queue();
            } else {
                channel.sendMessageEmbeds(embedBuilder.build())
                    .addFile(is, "board.png")
                    .queue();
            }
        } catch(IOException ex) {
            channel.sendMessage("Unexpected error: couldn't create board image").queue();
        }
    }
}
