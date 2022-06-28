package bot.builders.embed;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import othello.ai.Move;

import java.awt.*;
import java.util.List;

import static bot.utils.StringUtils.*;

public class AnalyzeEmbedBuilder
{
    private final EmbedBuilder embedBuilder;

    public AnalyzeEmbedBuilder() {
        embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.GREEN);
    }

    public AnalyzeEmbedBuilder setRankedMoves(List<Move> rankedMoves) {
        StringBuilder desc = new StringBuilder();
        desc.append("```");
        int count = 1;
        for (Move move : rankedMoves) {
            desc.append(rightPad(count + ")", 5))
                .append(rightPad(move.getTile().toString(), 5))
                .append(move.getHeuristic()).append(" ")
                .append("\n");
            count++;
        }
        desc.append("```");
        embedBuilder.setTitle("Move Analysis")
            .setDescription(desc)
            .setFooter("Positive heuristics are better for black, and negative heuristics are better for white");
        return this;
    }

    public MessageEmbed build() {
        return embedBuilder.build();
    }
}
