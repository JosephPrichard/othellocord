/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import models.Game;
import models.Player;
import models.Stats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import services.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestForfeitCommand {

    private GameService mock_gameService;
    private StatsService mock_statsService;
    private ForfeitCommand forfeitCommand;

    @BeforeEach
    public void beforeEach() {
        mock_gameService = mock(GameService.class);
        mock_statsService = mock(StatsService.class);
        forfeitCommand = new ForfeitCommand(mock_gameService, mock_statsService);
    }

    @Test
    public void whenCommand_success() {
        var mock_cmdCtx = mock(CommandContext.class);

        var callingPlayer = new Player(1000L);
        var otherPlayer = new Player(1001L);
        when(mock_cmdCtx.getPlayer()).thenReturn(callingPlayer);

        var game = Game.start(callingPlayer, otherPlayer);
        when(mock_gameService.getGame(any())).thenReturn(game);
        when(mock_statsService.writeStats(any()))
            .thenReturn(new Stats.Result());

        forfeitCommand.onCommand(mock_cmdCtx);

        verify(mock_gameService).deleteGame(game);
        verify(mock_statsService).writeStats(
            argThat((r) -> r.loser().equals(callingPlayer) && r.winner().equals(otherPlayer)));
    }

    @Test
    public void whenCommand_ifNoGame_stopEarly() {
        var mock_cmdCtx = mock(CommandContext.class);

        when(mock_cmdCtx.getPlayer()).thenReturn(new Player(1000L));
        when(mock_gameService.getGame(any())).thenReturn(null);

        forfeitCommand.onCommand(mock_cmdCtx);

        verify(mock_cmdCtx).reply(anyString());
    }
}
