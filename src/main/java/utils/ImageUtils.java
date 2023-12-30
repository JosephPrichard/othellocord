/*
 * Copyright (c) Joseph Prichard 2023.
 */

package utils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageUtils {

    public static BufferedImage readImage(String path) {
        try {
            var classLoader = ImageUtils.class.getClassLoader();
            var is = classLoader.getResourceAsStream(path);
            if (is != null) {
                return ImageIO.read(is);
            } else {
                throw new IOException();
            }
        } catch (IOException ex) {
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

    public static InputStream toPngInputStream(BufferedImage image) throws IOException {
        var os = new ByteArrayOutputStream();
        ImageIO.write(image, "png", os);
        return new ByteArrayInputStream(os.toByteArray());
    }
}
