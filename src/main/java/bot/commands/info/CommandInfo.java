package bot.commands.info;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandInfo
{
    private final String key;
    private final String description;
    private final List<CommandParam> params;

    public CommandInfo(String key, String description, CommandParam... params) {
        this.key = key;
        this.description = description;
        this.params = Arrays.stream(params).toList();
    }

    public CommandInfo(String key, String description) {
        this.key = key;
        this.description = description;
        this.params = new ArrayList<>();
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }

    public List<CommandParam> getParams() {
        return params;
    }
}
