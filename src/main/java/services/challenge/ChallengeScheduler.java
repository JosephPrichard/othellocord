/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.challenge;

import java.util.Map;
import java.util.concurrent.*;

import static utils.Logger.LOGGER;

// implementation that manages challenges by scheduling them using a scheduled executor service
public class ChallengeScheduler implements IChallengeScheduler {

    private final Map<Challenge, ScheduledFuture<?>> challenges;
    private final ScheduledExecutorService scheduler;

    public ChallengeScheduler() {
        this(new ConcurrentHashMap<>(), Executors.newSingleThreadScheduledExecutor());
    }

    public ChallengeScheduler(Map<Challenge, ScheduledFuture<?>> challenges, ScheduledExecutorService scheduler) {
        this.challenges = challenges;
        this.scheduler = scheduler;
    }

    public void createChallenge(Challenge challenge, Runnable onExpiry) {
        Runnable scheduled = () -> {
            onExpiry.run();
            challenges.remove(challenge);
            LOGGER.info("Challenge expired " + challenge.challenged().id() + " " + challenge.challenger().id());
        };
        var future = scheduler.schedule(scheduled, 60, TimeUnit.SECONDS);
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
