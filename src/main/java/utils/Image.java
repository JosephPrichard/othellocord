/*
 * Copyright (c) Joseph Prichard 2023.
 */

package utils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

public class Image
{
    public static BufferedImage readImage(String path) {
        try {
            var classLoader = Image.class.getClassLoader();
            var is = classLoader.getResourceAsStream(path);
            if (is != null) {
                return ImageIO.read(is);
            } else {
                throw new IOException();
            }
        } catch(IOException ex) {
            System.out.println(path + " failed to load");
            System.exit(1);
            return null;
        }
    }

    public static BufferedImage readResizedImage(String path, int size) {
        var oldImg = readImage(path);
        var resizedImg = oldImg.getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH);

        var newImg = new BufferedImage(size, size, oldImg.getType());

        var g = newImg.getGraphics();
        g.drawImage(resizedImg, 0, 0, null);

        return newImg;
    }

    public static void drawCenteredString(Graphics g, String text, Rectangle rect, Font font) {
        var metrics = g.getFontMetrics(font);
        // Determine the X coordinate for the text
        var x = rect.x + (rect.width - metrics.stringWidth(text)) / 2;
        // Determine the Y coordinate for the text (note we add the ascent, as in java 2d 0 is top of the screen)
        var y = rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
        g.setFont(font);
        g.drawString(text, x, y);
    }

    public static InputStream toPngIS(BufferedImage image) throws IOException {
        var os = new ByteArrayOutputStream();
        ImageIO.write(image, "png", os);
        return new ByteArrayInputStream(os.toByteArray());
    }
}
