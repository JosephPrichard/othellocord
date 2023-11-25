/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.message;

import services.Stats;
import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.*;
import java.util.List;

import static utils.Strings.*;

public class LeaderboardBuilder extends EmbedBuilder
{

    public LeaderboardBuilder() {
        setColor(Color.GREEN);
    }

    public LeaderboardBuilder setStats(List<Stats> statsList) {
        var desc = new StringBuilder();
        desc.append("```");
        var count = 1;
        for (var stats : statsList) {
            desc.append(rightPad(count + ")", 4))
                .append(leftPad(stats.getPlayer().getName(), 40))
                .append(leftPad(Float.toString(stats.getElo()), 16))
                .append("\n");
            count++;
        }
        desc.append("```");
        setTitle("Leaderboard").setDescription(desc.toString());
        return this;
    }
}
