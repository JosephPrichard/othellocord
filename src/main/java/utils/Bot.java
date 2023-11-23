/*
 * Copyright (c) Joseph Prichard 2023.
 */

package utils;

import services.Player;

public class Bot
{
    public static final int MAX_BOT_LEVEL = 15;
    public static final int MIN_DEPTH = 0;
    public static final int DEPTH_STEP = 1;

    public static int getDepthFromId(long id) {
        return MIN_DEPTH + (int) (id * DEPTH_STEP);
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
