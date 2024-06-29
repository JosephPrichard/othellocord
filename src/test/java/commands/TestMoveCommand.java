/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import othello.Tile;
import services.agent.IAgentDispatcher;
import services.agent.AgentEvent;
import services.game.Game;
import services.game.IGameService;
import services.game.exceptions.InvalidMoveException;
import services.game.exceptions.NotPlayingException;
import services.game.exceptions.TurnException;
import services.player.Player;
import services.stats.StatsResult;
import services.stats.IStatsService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestMoveCommand {

    private IGameService mock_gameService;
    private IStatsService mock_statsService;
    private IAgentDispatcher mock_agentDispatcher;
    private MoveCommand spy_moveCommand;

    @BeforeEach
    public void beforeEach() {
        mock_gameService = mock(IGameService.class);
        mock_statsService = mock(IStatsService.class);
        mock_agentDispatcher = mock(IAgentDispatcher.class);
        spy_moveCommand = spy(new MoveCommand(mock_gameService, mock_statsService, mock_agentDispatcher));
    }

    @Test
    public void whenCommand_ifPlayer_success() throws TurnException, NotPlayingException, InvalidMoveException {
        var mock_cmdCtx = mock(CommandContext.class);

        when(mock_cmdCtx.getStringParam("move")).thenReturn("c4");

        var callingPlayer = new Player(1000L);
        var otherPlayer = new Player(1001L);
        when(mock_cmdCtx.getPlayer()).thenReturn(callingPlayer);

        var game = new Game(otherPlayer, callingPlayer);
        when(mock_gameService.makeMove(any(), any()))
            .thenReturn(game);

        spy_moveCommand.onCommand(mock_cmdCtx);

        verify(mock_gameService).makeMove(callingPlayer, Tile.fromNotation("c4"));
        verify(spy_moveCommand).buildMoveSender(game, Tile.fromNotation("c4"));
    }

    @Test
    public void whenCommand_ifBot_success() throws TurnException, NotPlayingException, InvalidMoveException {
        var mock_cmdCtx = mock(CommandContext.class);

        when(mock_cmdCtx.getStringParam("move")).thenReturn("c4");

        var callingPlayer = new Player(1000L);
        var botPlayer = Player.Bot.create(1L);
        when(mock_cmdCtx.getPlayer()).thenReturn(callingPlayer);

        var spy_game = spy(new Game(botPlayer, callingPlayer));
        when(spy_game.getCurrentPlayer())
            .thenReturn(botPlayer);

        when(mock_gameService.makeMove(any(), any()))
            .thenReturn(spy_game);

        spy_moveCommand.onCommand(mock_cmdCtx);

        verify(mock_gameService).makeMove(callingPlayer, Tile.fromNotation("c4"));
        verify(spy_moveCommand).buildMoveSender(spy_game);
        verify(spy_moveCommand).doBotMove(mock_cmdCtx, spy_game);
        verify(mock_agentDispatcher).dispatchFindMoveEvent(
            argThat((req) -> req.equals(new AgentEvent<>(spy_game.board(), 1)))
        );
    }

    @Test
    public void whenCommand_ifGameOver_success() throws TurnException, NotPlayingException, InvalidMoveException {
        var mock_cmdCtx = mock(CommandContext.class);

        when(mock_cmdCtx.getStringParam("move")).thenReturn("c4");

        var whitePlayer = new Player(1000L);
        var blackPlayer = new Player(1001L);
        when(mock_cmdCtx.getPlayer()).thenReturn(whitePlayer);

        var spy_game = spy(new Game(blackPlayer, whitePlayer));
        when(spy_game.hasNoMoves()).thenReturn(true);
        when(spy_game.getWhiteScore()).thenReturn(19);
        when(spy_game.getBlackScore()).thenReturn(21);
        when(mock_statsService.writeStats(any()))
            .thenReturn(new StatsResult());

        when(mock_gameService.makeMove(any(), any()))
            .thenReturn(spy_game);

        spy_moveCommand.onCommand(mock_cmdCtx);

        verify(spy_moveCommand).onGameOver(spy_game, Tile.fromNotation("c4"));
        verify(mock_statsService).writeStats(
            argThat((arg) -> arg.loser().equals(whitePlayer)
                && arg.winner().equals(blackPlayer)
            ));
    }
}
