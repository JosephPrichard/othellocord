package bot.dtos;

import net.dv8tion.jda.api.entities.User;
import bot.utils.DiscordUtils;
import othello.utils.BotUtils;

public class PlayerDto
{
    private long id;
    private String name;

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
