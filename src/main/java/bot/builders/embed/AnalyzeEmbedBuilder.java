package bot.builders.embed;

import bot.builders.string.TableStringBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import othello.ai.Move;

import java.awt.*;
import java.util.List;

public class AnalyzeEmbedBuilder
{
    private final EmbedBuilder embedBuilder;

    public AnalyzeEmbedBuilder() {
        embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.GREEN);
    }

    public AnalyzeEmbedBuilder setRankedMoves(List<Move> rankedMoves) {
        TableStringBuilder builder = new TableStringBuilder(8, 8, 26);
        builder.addHeaders("Rank", "Move", "Heuristic");
        int count = 1;
        for (Move move : rankedMoves) {
            builder.addRow(Integer.toString(count), move.getPiece().toString(), Float.toString(move.getHeuristic()));
            count++;
        }
        String desc = "```\n" + builder + "```";
        embedBuilder.setTitle("Move Analysis")
            .setDescription(desc)
            .setFooter("Positive heuristics are better for black, and negative heuristics are better for white");
        return this;
    }

    public MessageEmbed build() {
        return embedBuilder.build();
    }
}
