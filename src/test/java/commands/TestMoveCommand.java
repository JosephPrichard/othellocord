/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import models.Game;
import models.Player;
import models.Stats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import domain.Tile;
import services.*;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestMoveCommand {

    private GameService mock_gameService;
    private StatsService mock_statsService;
    private AgentDispatcher mock_agentDispatcher;
    private MoveCommand spy_moveCommand;

    @BeforeEach
    public void beforeEach() {
        mock_gameService = mock(GameService.class);
        mock_statsService = mock(StatsService.class);
        mock_agentDispatcher = mock(AgentDispatcher.class);
        spy_moveCommand = spy(new MoveCommand(mock_gameService, mock_statsService, mock_agentDispatcher));
    }

    @Test
    public void whenCommand_ifPlayer_success() throws GameService.TurnException, GameService.NotPlayingException, GameService.InvalidMoveException {
        var mock_cmdCtx = mock(CommandContext.class);

        when(mock_cmdCtx.getStringParam("move")).thenReturn("c4");

        var callingPlayer = new Player(1000L);
        var otherPlayer = new Player(1001L);
        when(mock_cmdCtx.getPlayer()).thenReturn(callingPlayer);

        var game = Game.start(otherPlayer, callingPlayer);
        when(mock_gameService.makeMove(any(), any()))
            .thenReturn(game);

        spy_moveCommand.onCommand(mock_cmdCtx);

        verify(mock_gameService).makeMove(callingPlayer, Tile.fromNotation("c4"));
        verify(spy_moveCommand).buildMoveView(game, Tile.fromNotation("c4"));
    }

    @Test
    public void whenCommand_ifBot_success() throws GameService.TurnException, GameService.NotPlayingException, GameService.InvalidMoveException {
        var mock_cmdCtx = mock(CommandContext.class);

        when(mock_cmdCtx.getStringParam("move")).thenReturn("c4");

        var callingPlayer = new Player(1000L);
        var botPlayer = Player.Bot.create(1L);
        when(mock_cmdCtx.getPlayer()).thenReturn(callingPlayer);

        var spy_game = spy(Game.start(botPlayer, callingPlayer));
        when(spy_game.currentPlayer()).thenReturn(botPlayer);

        Answer<CompletableFuture<Tile.Move>> stubbedFindMove = invocation -> {
            // mock the response from the agent to be anything - this test doesn't need to know what it is
            CompletableFuture<Tile.Move> future = new CompletableFuture<>();
            future.complete(new Tile.Move(Tile.fromNotation("a1"), 0));
            return future;
        };
        doAnswer(stubbedFindMove).when(mock_agentDispatcher).findMove(any(), anyInt());

        when(mock_gameService.makeMove(any(), any())).thenReturn(spy_game);

        spy_moveCommand.onCommand(mock_cmdCtx);

        verify(mock_gameService).makeMove(callingPlayer, Tile.fromNotation("c4"));
        verify(spy_moveCommand).buildMoveView(spy_game);
        verify(spy_moveCommand).doBotMove(mock_cmdCtx, spy_game);
        verify(mock_agentDispatcher).findMove(eq(spy_game.board()), eq(1));
    }

    @Test
    public void whenCommand_ifGameOver_success() throws GameService.TurnException, GameService.NotPlayingException, GameService.InvalidMoveException {
        var mock_cmdCtx = mock(CommandContext.class);

        when(mock_cmdCtx.getStringParam("move")).thenReturn("c4");

        var whitePlayer = new Player(1000L);
        var blackPlayer = new Player(1001L);
        when(mock_cmdCtx.getPlayer()).thenReturn(whitePlayer);

        var spy_game = spy(Game.start(blackPlayer, whitePlayer));
        when(spy_game.isOver()).thenReturn(true);
        when(spy_game.whiteScore()).thenReturn(19);
        when(spy_game.blackScore()).thenReturn(21);

        when(mock_statsService.writeStats(any())).thenReturn(new Stats.Result());
        when(mock_gameService.makeMove(any(), any())).thenReturn(spy_game);

        spy_moveCommand.onCommand(mock_cmdCtx);

        verify(spy_moveCommand).onGameOver(spy_game, Tile.fromNotation("c4"));
        verify(mock_statsService).writeStats(eq(new Game.Result(blackPlayer, whitePlayer, false)));
    }
}
