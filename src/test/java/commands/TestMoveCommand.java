/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import othello.Tile;
import services.game.EvalRequest;
import services.game.Game;
import services.game.GameEvaluator;
import services.game.GameStorage;
import services.game.exceptions.InvalidMoveException;
import services.game.exceptions.NotPlayingException;
import services.game.exceptions.TurnException;
import services.player.Player;
import services.stats.StatsWriter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestMoveCommand {

    private GameStorage mock_gameStorage;
    private StatsWriter mock_statsWriter;
    private GameEvaluator mock_gameEvaluator;
    private MoveCommand spy_moveCommand;

    @BeforeEach
    public void beforeEach() {
        mock_gameStorage = mock(GameStorage.class);
        mock_statsWriter = mock(StatsWriter.class);
        mock_gameEvaluator = mock(GameEvaluator.class);
        spy_moveCommand = spy(new MoveCommand(mock_gameStorage, mock_statsWriter, mock_gameEvaluator));
    }

    @Test
    public void whenCommand_ifPlayer_success() throws TurnException, NotPlayingException, InvalidMoveException {
        var mock_cmdCtx = mock(CommandContext.class);

        when(mock_cmdCtx.getStringParam("move")).thenReturn("c4");

        var callingPlayer = new Player(1000L);
        var otherPlayer = new Player(1001L);
        when(mock_cmdCtx.getPlayer()).thenReturn(callingPlayer);

        var game = new Game(otherPlayer, callingPlayer);
        when(mock_gameStorage.makeMove((Player) any(), any()))
            .thenReturn(game);

        spy_moveCommand.onCommand(mock_cmdCtx);

        verify(mock_gameStorage).makeMove(callingPlayer, new Tile("c4"));
        verify(spy_moveCommand).onMoved(game, new Tile("c4"));
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

        when(mock_gameStorage.makeMove((Player) any(), any()))
            .thenReturn(spy_game);

        spy_moveCommand.onCommand(mock_cmdCtx);

        verify(mock_gameStorage).makeMove(callingPlayer, new Tile("c4"));
        verify(spy_moveCommand).onMoved(spy_game);
        verify(spy_moveCommand).doBotMove(mock_cmdCtx, spy_game);
        verify(mock_gameEvaluator).findBestMove(
            argThat((req) -> req.equals(new EvalRequest<>(spy_game, 1)))
        );
    }

    @Test
    public void whenCommand_ifGameOver_success() throws TurnException, NotPlayingException, InvalidMoveException {
        var mock_cmdCtx = mock(CommandContext.class);

        when(mock_cmdCtx.getStringParam("move")).thenReturn("c4");

        var callingPlayer = new Player(1000L);
        var otherPlayer = Player.Bot.create(1001L);
        when(mock_cmdCtx.getPlayer()).thenReturn(callingPlayer);

        var spy_game = spy(new Game(otherPlayer, callingPlayer));
        when(spy_game.isGameOver()).thenReturn(true);

        when(mock_gameStorage.makeMove((Player) any(), any()))
            .thenReturn(spy_game);

        spy_moveCommand.onCommand(mock_cmdCtx);

        verify(spy_moveCommand).onGameOver(spy_game, new Tile("c4"));
        verify(mock_statsWriter).updateStats(any());
    }
}
