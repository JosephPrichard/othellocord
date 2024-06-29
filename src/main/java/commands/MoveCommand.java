/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import commands.messaging.GameOverSender;
import commands.messaging.MessageSender;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction;
import othello.BoardRenderer;
import othello.Move;
import othello.OthelloBoard;
import othello.Tile;
import services.agent.AgentEvent;
import services.agent.IAgentDispatcher;
import services.game.Game;
import services.game.IGameService;
import services.game.exceptions.InvalidMoveException;
import services.game.exceptions.NotPlayingException;
import services.game.exceptions.TurnException;
import services.player.Player;
import services.stats.IStatsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static utils.Logger.LOGGER;

public class MoveCommand extends Command {

    private final IGameService gameService;
    private final IStatsService statsService;
    private final IAgentDispatcher agentDispatcher;

    public MoveCommand(IGameService gameService, IStatsService statsService, IAgentDispatcher agentDispatcher) {
        this.gameService = gameService;
        this.statsService = statsService;
        this.agentDispatcher = agentDispatcher;
    }

    public MessageSender buildMoveSender(Game game, Tile move) {
        var image = BoardRenderer.drawBoardMoves(game.board());
        return MessageSender.createGameViewSender(game, move, image);
    }

    public MessageSender buildMoveSender(Game game) {
        var image = BoardRenderer.drawBoardMoves(game.board());
        return MessageSender.createGameViewSender(game, image);
    }

    public MessageSender onGameOver(Game game, Tile move) {
        var result = game.createResult();
        var statsResult = statsService.writeStats(result);

        var image = BoardRenderer.drawBoard(game.board());
        return new GameOverSender()
            .setResults(result, statsResult)
            .addMoveMessage(result.winner(), move.toString())
            .addScoreMessage(game.getWhiteScore(), game.getBlackScore())
            .setTag(result)
            .setImage(image);
    }

    public void doBotMove(CommandContext ctx, Game game) {
        var currPlayer = game.getCurrentPlayer();
        var depth = Player.Bot.getDepthFromId(currPlayer.id());

        // queue an agent request which will find the best move, make the move, and send back a response
        AgentEvent<Move> event = new AgentEvent<>(game.board(), depth, (bestMove) -> {
            try {
                var newGame = gameService.makeMove(currPlayer, bestMove.tile());

                var sender = newGame.hasNoMoves() ?
                    onGameOver(newGame, bestMove.tile()) :
                    buildMoveSender(newGame, bestMove.tile());
                ctx.sendMessage(sender);
            } catch (Exception ex) {
                LOGGER.warning("Error occurred in an agent callback thread" + ex);
            }
        });
        agentDispatcher.dispatchFindMoveEvent(event);
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var strMove = Objects.requireNonNull(ctx.getStringParam("move"));
        var player = ctx.getPlayer();

        var move = Tile.fromNotation(strMove);
        try {
            var game = gameService.makeMove(player, move);

            if (game.hasNoMoves()) {
                var sender = onGameOver(game, move);
                ctx.sendReply(sender);
            } else {
                if (game.isAgainstBot()) {
                    var sender = buildMoveSender(game);
                    ctx.sendReply(sender);
                    doBotMove(ctx, game);
                } else {
                    var sender = buildMoveSender(game, move);
                    ctx.sendReply(sender);
                }
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
    public void onAutoComplete(CommandAutoCompleteInteraction interaction) {
        var player = new Player(interaction.getUser());

        var game = gameService.getGame(player);
        if (game != null) {
            var moves = game.findPotentialMoves();

            // don't display duplicate moves
            var duplicate = new boolean[OthelloBoard.getBoardSize()][OthelloBoard.getBoardSize()];

            List<Choice> choices = new ArrayList<>();
            for (var tile : moves) {
                var row = tile.row();
                var col = tile.col();

                if (!duplicate[row][col]) {
                    choices.add(new Choice(tile.toString(), tile.toString()));
                }
                duplicate[row][col] = true;
            }

            interaction.replyChoices(choices).queue();
        } else {
            interaction.replyChoices().queue();
        }
    }
}