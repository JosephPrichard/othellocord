/*
 * Copyright (c) Joseph Prichard 2024.
 */

package utils;

import java.io.InputStream;
import java.util.Scanner;

import static utils.LogUtils.LOGGER;

public class ConfigUtils {
    public static String readJDAToken(InputStream envFile) {
        if (envFile == null) {
            LOGGER.error("Needs a .env file with a BOT_TOKEN field");
            System.exit(1);
        }

        String botToken = null;

        Scanner envScanner = new Scanner(envFile);
        while (envScanner.hasNext()) {
            String line = envScanner.nextLine();
            String[] tokens = line.split("=");
            if (tokens.length < 2) {
                LOGGER.error("The env file line must have at least two tokens");
                System.exit(1);
            }

            if (tokens[0].equals("BOT_TOKEN")) {
                botToken = tokens[1];
            }
        }
        if (botToken == null) {
            LOGGER.error("You have to provide the BOT_TOKEN key  in the .env file");
            System.exit(1);
        }

        return botToken;
    }
}
