/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import services.game.Game;
import services.game.GameStorage;
import services.player.Player;
import services.stats.StatsResult;
import services.stats.StatsWriter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestForfeitCommand {

    private GameStorage mock_gameStorage;
    private StatsWriter mock_statsWriter;
    private ForfeitCommand forfeitCommand;

    @BeforeEach
    public void beforeEach() {
        mock_gameStorage = mock(GameStorage.class);
        mock_statsWriter = mock(StatsWriter.class);
        forfeitCommand = new ForfeitCommand(mock_gameStorage, mock_statsWriter);
    }

    @Test
    public void whenCommand_success() {
        var mock_cmdCtx = mock(CommandContext.class);

        var callingPlayer = new Player(1000L);
        var otherPlayer = new Player(1001L);
        when(mock_cmdCtx.getPlayer()).thenReturn(callingPlayer);

        var game = new Game(callingPlayer, otherPlayer);
        when(mock_gameStorage.getGame(any())).thenReturn(game);
        when(mock_statsWriter.writeStats(any()))
            .thenReturn(new StatsResult());

        forfeitCommand.onCommand(mock_cmdCtx);

        verify(mock_gameStorage).deleteGame(game);
        verify(mock_statsWriter).writeStats(
            argThat((r) -> r.loser().equals(callingPlayer)
                && r.winner().equals(otherPlayer)
            ));
    }

    @Test
    public void whenCommand_ifNoGame_stopEarly() {
        var mock_cmdCtx = mock(CommandContext.class);

        when(mock_cmdCtx.getPlayer()).thenReturn(new Player(1000L));
        when(mock_gameStorage.getGame(any())).thenReturn(null);

        forfeitCommand.onCommand(mock_cmdCtx);

        verify(mock_cmdCtx).reply(anyString());
    }
}
