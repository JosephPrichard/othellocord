/*
 * Copyright (c) Joseph Prichard 2024.
 */

package commands;

import lombok.Getter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.InteractionHook;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Getter
public class GameView {

    private final EmbedBuilder embed;
    private BufferedImage image;
    private String message = "";

    public GameView(EmbedBuilder embed) {
        this.embed = embed;
        this.embed.setColor(Color.GREEN);
    }

    public GameView setImage(BufferedImage image) {
        this.image = image;
        return this;
    }

    public GameView setMessage(String message) {
        this.message = message;
        return this;
    }

    public GameView setTitle(String title) {
        embed.setTitle(title);
        return this;
    }

    private static InputStream toPngInputStream(BufferedImage image) throws IOException {
        var os = new ByteArrayOutputStream();
        ImageIO.write(image, "png", os);
        return new ByteArrayInputStream(os.toByteArray());
    }

    public void editUsingHook(InteractionHook hook) {
        try {
            var embed = getEmbed();
            var message = getMessage();

            var is = toPngInputStream(getImage());
            embed.setImage("attachment://image.png");

            hook.editOriginalEmbeds(embed.build())
                .retainFilesById(new long[]{}) // retain none of the ids: aka get rid of all the files
                .addFile(is, "image.png")
                .queue();
            if (!message.isEmpty()) {
                hook.editOriginal(message).queue();
            }
        } catch (IOException ex) {
            hook.editOriginal("Unexpected error: couldn't create image").queue();
        }
    }
}
