/*
 * Copyright (c) Joseph Prichard 2024.
 */

package commands.views;

import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.*;
import java.awt.image.BufferedImage;

public class GameView {

    private final EmbedBuilder embed;
    private BufferedImage image;
    private String message = "";

    public GameView(EmbedBuilder embed) {
        this.embed = embed;
        this.embed.setColor(Color.GREEN);
    }

    public EmbedBuilder getEmbed() {
        return embed;
    }

    public BufferedImage getImage() {
        return image;
    }

    public GameView setImage(BufferedImage image) {
        this.image = image;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public GameView setMessage(String message) {
        this.message = message;
        return this;
    }

    public GameView setTitle(String title) {
        embed.setTitle(title);
        return this;
    }
}
