/*
 * Copyright (c) Joseph Prichard 2023.
 */

package utils;

import services.game.Player;

public class Bot {
    public static final int MAX_BOT_LEVEL = 5;

    public static int getDepthFromId(long id) {
        return switch ((int) id) {
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 5;
            case 4 -> 8;
            case 5 -> 10;
            case 6 -> 15;
            default -> throw new IllegalStateException("Invalid bot id: " + id);
        };
    }

    public static boolean isValidLevel(long level) {
        return level >= 1 && level <= MAX_BOT_LEVEL;
    }

    public static String getBotName(long id) {
        return "OthelloBot level " + id;
    }

    public static Player create(long level) {
        return new Player(level, getBotName(level));
    }

    public static boolean isBotId(long id) {
        return id <= MAX_BOT_LEVEL;
    }
}
