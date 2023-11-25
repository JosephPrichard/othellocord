/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.commands;

import services.GameService;
import services.StatsService;
import services.Game;
import services.GameResult;
import services.Player;
import discord.message.GameOverSender;
import discord.renderers.OthelloBoardRenderer;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.image.BufferedImage;
import java.util.logging.Logger;

public class ForfeitCommand extends Command
{
    private final Logger logger = Logger.getLogger("command.forfeit");
    private final GameService gameService;
    private final StatsService statsService;
    private final OthelloBoardRenderer boardRenderer;

    public ForfeitCommand(
        GameService gameService,
        StatsService statsService,
        OthelloBoardRenderer boardRenderer
    ) {
        super("forfeit", "Forfeits the user's current game");
        this.gameService = gameService;
        this.statsService = statsService;
        this.boardRenderer = boardRenderer;
    }

    @Override
    public void doCommand(CommandContext ctx) {
        var event = ctx.getEvent();
        var channel = event.getChannel();

        var player = new Player(event.getAuthor());

        var game = gameService.getGame(player);
        if (game == null) {
            channel.sendMessage("You're not currently in a game.").queue();
            return;
        }

        var image = boardRenderer.drawBoard(game.getBoard());

        // remove game from storage
        gameService.deleteGame(game);
        // update elo from game result
        var result = game.getForfeitResult();
        statsService.updateStats(result);
        // send embed response
        new GameOverSender()
            .setGame(result)
            .addForfeitMessage(result.getWinner())
            .setTag(result)
            .setImage(image)
            .sendMessage(channel);

        logger.info(player + " has forfeited");
    }
}
