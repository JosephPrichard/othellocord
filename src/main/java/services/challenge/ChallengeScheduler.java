/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.challenge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static utils.Logger.LOGGER;

public class ChallengeScheduler implements ChallengeManager {

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
        Runnable scheduled = () -> {
            onExpiry.run();
            challenges.remove(challenge);
            LOGGER.info("Challenge expired " + challenge.challenged().getId() + " " + challenge.challenger().getId());
        };
        var future = scheduler.schedule(scheduled, 30, TimeUnit.SECONDS);
        challenges.put(challenge, future);
    }

    public boolean acceptChallenge(Challenge challenge) {
        var future = challenges.remove(challenge);
        if (future != null) {
            future.cancel(false);
            return true;
        }
        return false;
    }
}
