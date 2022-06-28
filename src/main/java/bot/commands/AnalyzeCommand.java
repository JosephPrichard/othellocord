package bot.commands;

import bot.commands.abstracts.CommandContext;
import bot.commands.abstracts.Command;
import bot.dtos.AiRequestDto;
import bot.dtos.GameDto;
import bot.dtos.PlayerDto;
import bot.builders.embed.AnalyzeEmbedBuilder;
import bot.services.GameService;
import bot.services.OthelloAiService;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import othello.ai.Move;
import othello.utils.NumberUtils;

import java.util.List;
import java.util.logging.Logger;

public class AnalyzeCommand extends Command
{
    private final Logger logger = Logger.getLogger("command.analyze");
    private final GameService gameService;
    private final OthelloAiService aiService;

    public AnalyzeCommand(GameService gameService, OthelloAiService aiService) {
        super("analyze", "Runs an analysis of the board until a given depth between 5 and 15", 0, "depth");
        this.gameService = gameService;
        this.aiService = aiService;
    }

    @Override
    protected void doCommand(CommandContext ctx) {
        MessageReceivedEvent event = ctx.getEvent();
        MessageChannel channel = event.getChannel();

        // retrieve depth parameter and perform type validation
        Integer depth = 5;
        String depthStr = ctx.getParam("depth");
        if (depthStr != null) {
            depth = NumberUtils.parseIntOrNull(depthStr);
            if (depth == null) {
                channel.sendMessage("Depth must be a number.").queue();
                return;
            }
        }

        // check if depth is within range
        if (depth < 5 || depth > 15) {
            channel.sendMessage("Invalid depth. Type !help analyze for valid depths.").queue();
            return;
        }

        PlayerDto player = new PlayerDto(event.getAuthor());

        // fetch game to analyze
        GameDto game = gameService.getGame(player);
        if (game == null) {
            channel.sendMessage("You're not currently in a game.").queue();
            return;
        }

        // send starting message, then add queue an ai request, send back the results in a message when its done
        int d = depth;
        channel.sendMessage("Analyzing... Wait a second...").queue(m -> {
            logger.info("Starting board state analysis");

            aiService.findRankedMoves(
                new AiRequestDto<>(game.getBoard(), d, (List<Move> rankedMoves) -> {
                    MessageEmbed embed = new AnalyzeEmbedBuilder()
                        .setRankedMoves(rankedMoves)
                        .build();

                    m.editMessage("<@" + player + "> ").queue();
                    m.editMessageEmbeds(embed).queue();

                    logger.info("Finished board state analysis");
                })
            );
        });
    }
}
