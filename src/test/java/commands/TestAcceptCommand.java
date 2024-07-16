/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import services.challenge.Challenge;
import services.challenge.IChallengeScheduler;
import services.game.Game;
import services.game.IGameService;
import services.game.exceptions.AlreadyPlayingException;
import services.player.Player;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestAcceptCommand {

    private IGameService mock_gameService;
    private IChallengeScheduler mock_challengeScheduler;
    private AcceptCommand acceptCommand;

    @BeforeEach
    public void beforeEach() {
        mock_gameService = mock(IGameService.class);
        mock_challengeScheduler = mock(IChallengeScheduler.class);
        acceptCommand = new AcceptCommand(mock_gameService, mock_challengeScheduler);
    }

    @Test
    public void whenCommand_success() throws AlreadyPlayingException {
        var mock_cmdCtx = mock(CommandContext.class);

        var callingPlayer = new Player(1000L);
        var otherPlayer = new Player(1001L);

        when(mock_cmdCtx.getPlayer()).thenReturn(callingPlayer);
        when(mock_cmdCtx.getPlayerParam("challenger")).thenReturn(otherPlayer);

        when(mock_challengeScheduler.acceptChallenge(any())).thenReturn(true);

        when(mock_gameService.createGame(callingPlayer, otherPlayer))
            .thenReturn(Game.start(callingPlayer, otherPlayer));

        acceptCommand.onCommand(mock_cmdCtx);

        verify(mock_cmdCtx).replyView(any());
        verify(mock_challengeScheduler).acceptChallenge(new Challenge(callingPlayer, otherPlayer));
    }

    @Test
    public void whenCommand_withNoChallenge() throws AlreadyPlayingException {
        var mock_cmdCtx = mock(CommandContext.class);

        var callingPlayer = new Player(1000L);
        var otherPlayer = new Player(1001L);

        when(mock_cmdCtx.getPlayer()).thenReturn(callingPlayer);
        when(mock_cmdCtx.getPlayerParam("challenger")).thenReturn(otherPlayer);

        when(mock_challengeScheduler.acceptChallenge(any())).thenReturn(false);

        acceptCommand.onCommand(mock_cmdCtx);

        verify(mock_cmdCtx).reply(any());
        verify(mock_gameService, times(0)).createGame(any(), any());
    }
}
