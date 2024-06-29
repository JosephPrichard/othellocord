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

public class TestChallengeCommand {

    private IGameService mock_gameService;
    private IChallengeScheduler mock_challengeScheduler;
    private ChallengeCommand challengeCommand;

    @BeforeEach
    public void beforeEach() {
        mock_gameService = mock(IGameService.class);
        mock_challengeScheduler = mock(IChallengeScheduler.class);
        challengeCommand = new ChallengeCommand(mock_gameService, mock_challengeScheduler);
    }

    @Test
    public void whenUserCommand_success() {
        var mock_cmdCtx = mock(CommandContext.class);

        var callingPlayer = new Player(1000L);
        var otherPlayer = new Player(1001L);

        when(mock_cmdCtx.getPlayer()).thenReturn(callingPlayer);
        when(mock_cmdCtx.getPlayerParam("opponent")).thenReturn(otherPlayer);

        challengeCommand.doUserCommand(mock_cmdCtx);

        verify(mock_challengeScheduler).createChallenge(eq(new Challenge(otherPlayer, callingPlayer)), any());
    }

    @Test
    public void whenBotCommand_success() throws AlreadyPlayingException {
        var mock_cmdCtx = mock(CommandContext.class);

        var callingPlayer = new Player(1000L);
        when(mock_cmdCtx.getPlayer()).thenReturn(callingPlayer);
        when(mock_cmdCtx.getLongParam("level")).thenReturn(3L);

        when(mock_gameService.createBotGame(any(), anyLong()))
            .thenReturn(new Game(Player.Bot.create(3), callingPlayer)); // the challenging player is always black

        challengeCommand.doBotCommand(mock_cmdCtx);

        verify(mock_gameService).createBotGame(callingPlayer, 3);
    }

    @Test
    public void whenBotCommand_fail() throws AlreadyPlayingException {
        var mock_cmdCtx = mock(CommandContext.class);

        var callingPlayer = new Player(1000L);
        when(mock_cmdCtx.getPlayer()).thenReturn(callingPlayer);
        when(mock_cmdCtx.getLongParam("level")).thenReturn(1000L);

        challengeCommand.doBotCommand(mock_cmdCtx);

        verify(mock_cmdCtx).reply(any());
        verify(mock_gameService, times(0)).createBotGame(any(), anyLong());
    }
}
