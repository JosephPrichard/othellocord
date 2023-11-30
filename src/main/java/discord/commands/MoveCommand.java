/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.commands;

import discord.message.GameOverSender;
import discord.message.GameViewSender;
import discord.message.MessageSender;
import discord.renderers.OthelloBoardRenderer;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import othello.Move;
import othello.Tile;
import services.*;
import services.exceptions.InvalidMoveException;
import services.exceptions.NotPlayingException;
import services.exceptions.TurnException;
import utils.Bot;

import java.util.stream.Collectors;

import static utils.Logger.LOGGER;

public class MoveCommand extends Command
{
    private final GameService gameService;
    private final StatsService statsService;
    private final AgentService agentService;
    private final OthelloBoardRenderer boardRenderer;

    public MoveCommand(
        GameService gameService,
        StatsService statsService,
        AgentService agentService,
        OthelloBoardRenderer boardRenderer
    ) {
        super("move", "Makes a move on user's current game",
            new OptionData(OptionType.STRING, "move", "Move to make on the board", true, true));
        this.gameService = gameService;
        this.statsService = statsService;
        this.agentService = agentService;
        this.boardRenderer = boardRenderer;
    }

    public MessageSender onMoved(Game game, Tile move) {
        var image = boardRenderer.drawBoardMoves(game.getBoard());
        return new GameViewSender()
            .setGame(game, move)
            .setTag(game)
            .setImage(image);
    }

    public MessageSender onMoved(Game game) {
        var image = boardRenderer.drawBoardMoves(game.getBoard());
        return new GameViewSender().setGame(game).setImage(image);
    }

    public MessageSender onGameOver(Game game, Tile move) {
        // update elo the elo of the players
        var result = game.getResult();
        statsService.updateStats(result);
        // render board and send back message
        var image = boardRenderer.drawBoard(game.getBoard());
        return new GameOverSender()
            .setGame(result)
            .addMoveMessage(result.getWinner(), move.toString())
            .addScoreMessage(game.getWhiteScore(), game.getBlackScore())
            .setTag(result)
            .setImage(image);
    }

    private void sendAgentRequest(CommandContext ctx, Game game) {
        // queue an agent request which will find the best move, make the move, and send back a response
        var depth = Bot.getDepthFromId(game.getCurrentPlayer().getId());
        var r = new AgentRequest<>(game, depth, (Move bestMove) -> {
            gameService.makeMove(game, bestMove.getTile());

            MessageSender sender;
            if (!game.isGameOver()) {
                sender = onMoved(game, bestMove.getTile());
            } else {
                sender = onGameOver(game, bestMove.getTile());
            }
            sender.sendMessage(ctx);
        });
        agentService.findBestMove(r);
    }

    @Override
    public void doCommand(CommandContext ctx) {
        var strMove = ctx.getParam("move").getAsString();
        var player = new Player(ctx.getAuthor());

        var move = new Tile(strMove);
        try {
            var game = gameService.makeMove(player, move);

            if (!game.isGameOver()) {
                if (!game.isAgainstBot()) {
                    var sender = onMoved(game, move);
                    sender.sendReply(ctx);
                } else {
                    var sender = onMoved(game);
                    sender.sendReply(ctx);
                    sendAgentRequest(ctx, game);
                }
            } else {
                var sender = onGameOver(game, move);
                sender.sendReply(ctx);
            }

            LOGGER.info("Player " + player + " made move on game");
        } catch (TurnException e) {
            ctx.reply("It isn't your turn.");
        } catch (NotPlayingException e) {
            ctx.reply("You're not currently in a game.");
        } catch (InvalidMoveException e) {
            ctx.reply("Can't make a move to " + strMove + ".");
        }
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        var player = new Player(event.getUser());
        var game = gameService.getGame(player);
        if (game != null) {
            var moves = game.getBoard().findPotentialMoves();
            var choices = moves.stream()
                .map((tile) -> new Choice(tile.toString(), tile.toString()))
                .collect(Collectors.toList());
            event.replyChoices(choices).queue();
        }
    }
}