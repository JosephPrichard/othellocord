/*
 * Copyright (c) Joseph Prichard 2024.
 */

package services;

import lombok.AllArgsConstructor;
import models.Challenge;

import java.util.Map;
import java.util.concurrent.*;

import static utils.LogUtils.LOGGER;

@AllArgsConstructor
public class ChallengeScheduler {

    private final Map<Challenge, ScheduledFuture<?>> challenges;
    private final ScheduledExecutorService scheduler;

    public ChallengeScheduler() {
        this(new ConcurrentHashMap<>(), Executors.newSingleThreadScheduledExecutor());
    }

    public void createChallenge(Challenge challenge, Runnable onExpiry) {
        Runnable scheduled = () -> {
            onExpiry.run();
            challenges.remove(challenge);
            LOGGER.info("Challenge expired {} {}", challenge.getChallenged().getId(), challenge.getChallenger().getId());
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
