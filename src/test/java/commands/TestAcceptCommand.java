/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import othello.BoardRenderer;
import services.challenge.ChallengeManager;
import services.game.GameStorage;

import static org.mockito.Mockito.mock;

public class TestAcceptCommand {

    @Spy
    private GameStorage mock_gameStorage;
    @Spy
    private ChallengeManager mock_challengeScheduler;
    @Spy
    private BoardRenderer mock_boardRenderer;
    @InjectMocks
    private AcceptCommand acceptCommand;

    @Test
    public void testDoCommand() {
        var ctx = mock(CommandContext.class);
        acceptCommand.doCommand(ctx);
    }
}
