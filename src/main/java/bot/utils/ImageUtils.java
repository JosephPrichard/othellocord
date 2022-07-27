package bot.utils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;

public class ImageUtils
{
    public static BufferedImage readImage(String path) {
        try {
            ClassLoader classLoader = ImageUtils.class.getClassLoader();
            InputStream is = classLoader.getResourceAsStream(path);
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
        BufferedImage oldImg = readImage(path);
        java.awt.Image resizedImg = oldImg.getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH);

        BufferedImage newImg = new BufferedImage(size, size, oldImg.getType());

        Graphics g = newImg.getGraphics();
        g.drawImage(resizedImg, 0, 0, null);

        return newImg;
    }

    public static void drawCenteredString(Graphics g, String text, Rectangle rect, Font font) {
        FontMetrics metrics = g.getFontMetrics(font);
        // Determine the X coordinate for the text
        int x = rect.x + (rect.width - metrics.stringWidth(text)) / 2;
        // Determine the Y coordinate for the text (note we add the ascent, as in java 2d 0 is top of the screen)
        int y = rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
        g.setFont(font);
        g.drawString(text, x, y);
    }

    public static InputStream toPngIS(BufferedImage image) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(image, "png", os);
        return new ByteArrayInputStream(os.toByteArray());
    }
}
