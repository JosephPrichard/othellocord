/*
 * Copyright (c) Joseph Prichard 2024.
 */

package models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.dv8tion.jda.api.entities.User;

@Data
@AllArgsConstructor
public class Player {
    private long id;
    @EqualsAndHashCode.Exclude
    private String name;

    public Player(long id) {
        this(id, "");
    }

    public Player(int id) {
        this((long) id);
    }

    public Player(User user) {
        this(parseDiscordID(user.getId()), user.getName());
    }

    public static long parseDiscordID(String id) {
        String strippedId = id.replaceAll("\\D", "");
        return Long.parseLong(strippedId);
    }

    public boolean isBot() {
        return Bot.isBotId(id);
    }

    public String toAtString() {
        return "<@" + id + "> ";
    }

    public static class Bot {

        public static final int MAX_BOT_LEVEL = 6;

        public static String name(long id) {
            return "OthelloBot level " + id;
        }

        public static Player create(long level) {
            return new Player(level, name(level));
        }

        public static boolean isBotId(long id) {
            return id <= MAX_BOT_LEVEL;
        }

        public static boolean isInvalidLevel(long level) {
            return level < 1 || level > MAX_BOT_LEVEL;
        }

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
    }
}
