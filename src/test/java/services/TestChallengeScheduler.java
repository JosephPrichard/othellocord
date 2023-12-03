/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import services.challenge.Challenge;
import services.challenge.ChallengeScheduler;
import services.player.Player;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.Mockito.*;

public class TestChallengeScheduler {

    @Mock
    private ScheduledExecutorService mock_schedulerService;
    @InjectMocks
    private ChallengeScheduler challengeScheduler;

    @Test
    public void testCreateChallenge() {
        var challenging = new Player(1000, "Player1");
        var challenged = new Player(1001, "Player2");
        var challenge = new Challenge(challenged, challenging);

        when(mock_schedulerService.schedule(any(Runnable.class), anyLong(), any()))
            .thenReturn(null);

        challengeScheduler.createChallenge(challenge, () -> {});

        verify(mock_schedulerService)
            .schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    public void testAcceptChallenge() {
        var challenging = new Player(1000, "Player1");
        var challenged = new Player(1001, "Player2");
        var challenge = new Challenge(challenged, challenging);

        HashMap<Challenge, ScheduledFuture<?>> map = new HashMap<>();
        map.put(challenge, any(ScheduledFuture.class));
        var challengeScheduler = new ChallengeScheduler(map, mock_schedulerService);

        var canceled = challengeScheduler.acceptChallenge(challenge);
        Assertions.assertTrue(canceled);
    }
}
