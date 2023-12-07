/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import services.challenge.Challenge;
import services.challenge.ChallengeManager;
import services.game.Game;
import services.game.GameStorage;
import services.game.exceptions.AlreadyPlayingException;
import services.player.Player;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestAcceptCommand {

    private GameStorage mock_gameStorage;
    private ChallengeManager mock_challengeManager;
    private AcceptCommand acceptCommand;

    @BeforeEach
    public void beforeEach() {
        mock_gameStorage = mock(GameStorage.class);
        mock_challengeManager = mock(ChallengeManager.class);
        acceptCommand = new AcceptCommand(mock_gameStorage, mock_challengeManager);
    }

    @Test
    public void whenCommand_success() throws AlreadyPlayingException {
        var mock_cmdCtx = mock(CommandContext.class);

        var callingPlayer = new Player(1000L);
        var otherPlayer = new Player(1001L);

        when(mock_cmdCtx.getPlayer()).thenReturn(callingPlayer);
        when(mock_cmdCtx.getPlayerParam("challenger")).thenReturn(otherPlayer);

        when(mock_challengeManager.acceptChallenge(any())).thenReturn(true);

        when(mock_gameStorage.createGame(callingPlayer, otherPlayer))
            .thenReturn(new Game(callingPlayer, otherPlayer));

        acceptCommand.onCommand(mock_cmdCtx);

        verify(mock_cmdCtx).replyWithSender(any());
        verify(mock_challengeManager).acceptChallenge(new Challenge(callingPlayer, otherPlayer));
    }

    @Test
    public void whenCommand_withNoChallenge() throws AlreadyPlayingException {
        var mock_cmdCtx = mock(CommandContext.class);

        var callingPlayer = new Player(1000L);
        var otherPlayer = new Player(1001L);

        when(mock_cmdCtx.getPlayer()).thenReturn(callingPlayer);
        when(mock_cmdCtx.getPlayerParam("challenger")).thenReturn(otherPlayer);

        when(mock_challengeManager.acceptChallenge(any())).thenReturn(false);

        acceptCommand.onCommand(mock_cmdCtx);

        verify(mock_cmdCtx).reply(any());
        verify(mock_gameStorage, times(0)).createGame(any(), any());
    }
}
