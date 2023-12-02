/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import services.game.Challenge;
import services.game.ChallengeScheduler;
import services.game.Player;

import java.util.HashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

public class TestChallengeScheduler {

    private ScheduledExecutorService scheduler;
    private ChallengeScheduler challengeScheduler;

    @BeforeEach
    public void beforeEach() {
        scheduler = mock(ScheduledExecutorService.class);
    }

    @Test
    public void testCreateChallenge() {
        var challengeScheduler = new ChallengeScheduler(scheduler);

        var challenging = new Player(1000, "Player1");
        var challenged = new Player(1001, "Player2");
        var challenge = new Challenge(challenged, challenging);

        when(scheduler.schedule(any(Runnable.class), anyLong(), any()))
            .thenReturn(null);

        challengeScheduler.createChallenge(challenge, () -> {});

        verify(scheduler)
            .schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    public void testAcceptChallenge() {
        var challenging = new Player(1000, "Player1");
        var challenged = new Player(1001, "Player2");
        var challenge = new Challenge(challenged, challenging);

        HashMap<Challenge, ScheduledFuture<?>> map = new HashMap<>();
        map.put(challenge, any(ScheduledFuture.class));
        var challengeScheduler = new ChallengeScheduler(map, scheduler);

        var canceled = challengeScheduler.acceptChallenge(challenge);
        Assertions.assertTrue(canceled);
    }
}
