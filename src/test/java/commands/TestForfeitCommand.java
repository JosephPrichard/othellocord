/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import services.game.Game;
import services.game.GameStorage;
import services.player.Player;
import services.stats.StatsWriter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestForfeitCommand {

    private GameStorage mock_gameStorage;
    private StatsWriter mock_statsWriter;
    private final ExecutorService ioTaskExecutor = Executors.newSingleThreadExecutor();
    private ForfeitCommand forfeitCommand;

    @BeforeEach
    public void beforeEach() {
        mock_gameStorage = mock(GameStorage.class);
        mock_statsWriter = mock(StatsWriter.class);
        forfeitCommand = new ForfeitCommand(mock_gameStorage, mock_statsWriter, ioTaskExecutor);
    }

    @Test
    public void whenCommand_success() throws InterruptedException {
        var mock_cmdCtx = mock(CommandContext.class);

        var callingPlayer = new Player(1000L);
        var otherPlayer = new Player(1001L);
        when(mock_cmdCtx.getPlayer()).thenReturn(callingPlayer);

        var game = new Game(callingPlayer, otherPlayer);
        when(mock_gameStorage.getGame(any())).thenReturn(game);

        forfeitCommand.onCommand(mock_cmdCtx);

        // wait for all io tasks to finish before we verify
        ioTaskExecutor.shutdown();
        Assertions.assertTrue((ioTaskExecutor.awaitTermination(1, TimeUnit.SECONDS)));

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
