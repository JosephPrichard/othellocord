package bot.dtos;

import net.dv8tion.jda.api.entities.User;
import bot.utils.DiscordUtils;

public class PlayerDto
{
    private static final long BOT_ID = -1;
    private static final String BOT_NAME = "Bot";

    private long id;
    private String name;

    public static PlayerDto Bot() {
        return new PlayerDto(BOT_ID, BOT_NAME);
    }

    public PlayerDto() {}

    public PlayerDto(long id) {
        this.id = id;
    }

    public PlayerDto(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public PlayerDto(User user) {
        this.id = DiscordUtils.toLongId(user.getId());
        this.name = user.getAsTag();
    }

    public boolean isBot() {
        return id == BOT_ID;
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
        PlayerDto player = (PlayerDto) o;
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
