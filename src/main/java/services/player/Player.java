/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.player;

import net.dv8tion.jda.api.entities.User;
import utils.Bot;

public class Player {

    private long id;
    private String name;

    public Player() {
    }

    public Player(long id) {
        this.id = id;
    }

    public Player(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public static long parseDiscordID(String id) {
        // removes all non digit characters
        var strippedId = id.replaceAll("\\D", "");
        return Long.parseLong(strippedId);
    }

    public Player(User user) {
        this.id = parseDiscordID(user.getId());
        this.name = user.getAsTag();
    }

    public boolean isBot() {
        return Bot.isBotId(id);
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var player = (Player) o;
        return id == player.getId();
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return Long.toString(id);
    }
}
