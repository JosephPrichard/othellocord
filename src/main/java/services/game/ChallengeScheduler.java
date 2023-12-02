/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.game;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static utils.Logger.LOGGER;

public class ChallengeScheduler
{
    private final Map<Challenge, ScheduledFuture<?>> challenges;
    private final ScheduledExecutorService scheduler;

    public ChallengeScheduler(ScheduledExecutorService scheduler) {
        this(new ConcurrentHashMap<>(), scheduler);
    }

    public ChallengeScheduler(Map<Challenge, ScheduledFuture<?>> challenges, ScheduledExecutorService scheduler) {
        this.challenges = challenges;
        this.scheduler = scheduler;
    }

    public void createChallenge(Challenge challenge, Runnable onExpiry) {
        var challenged = challenge.challenged();
        var challenger = challenge.challenger();

        var future = scheduler.schedule(() -> {
            onExpiry.run();
            challenges.remove(new Challenge(challenged, challenger));
            LOGGER.info("Challenge expired " + challenged.getId() + " " + challenger.getId());
        }, 30, TimeUnit.SECONDS);

        challenges.put(new Challenge(challenged, challenger), future);
    }

    public boolean acceptChallenge(Challenge challenge) {
        var challenged = challenge.challenged();
        var challenger = challenge.challenger();

        var future = challenges.remove(new Challenge(challenged, challenger));
        if (future != null) {
            future.cancel(false);
            return true;
        }
        return false;
    }
}
