package modules;

import net.dv8tion.jda.api.entities.User;
import utils.DiscordUtils;
import utils.BotUtils;

public class Player
{
    private long id;
    private String name;

    public Player() {}

    public Player(long id) {
        this.id = id;
    }

    public Player(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Player(User user) {
        this.id = DiscordUtils.toLongId(user.getId());
        this.name = user.getAsTag();
    }

    public boolean isBot() {
        return BotUtils.isBotId(id);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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
        Player player = (Player) o;
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
