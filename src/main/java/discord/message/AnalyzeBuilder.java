/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.message;

import net.dv8tion.jda.api.EmbedBuilder;
import othello.Move;

import java.awt.*;
import java.util.List;

import static utils.Strings.*;

public class AnalyzeBuilder extends EmbedBuilder
{

    public AnalyzeBuilder() {
        setColor(Color.GREEN);
    }

    public AnalyzeBuilder setRankedMoves(List<Move> rankedMoves) {
        var desc = new StringBuilder();
        desc.append("```");
        var count = 1;
        for (var move : rankedMoves) {
            desc.append(rightPad(count + ")", 5))
                .append(rightPad(move.getTile().toString(), 5))
                .append(move.getHeuristic()).append(" ")
                .append("\n");
            count++;
        }
        desc.append("```");
        setTitle("Move Analysis")
            .setDescription(desc)
            .setFooter("Positive heuristics are better for black, and negative heuristics are better for white");
        return this;
    }
}
