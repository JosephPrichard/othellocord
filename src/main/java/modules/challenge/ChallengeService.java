package modules.challenge;

import modules.Player;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class ChallengeService
{
    private final Logger logger = Logger.getLogger("service.challenge");
    private final Map<Challenge, ScheduledFuture<?>> challenges = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    public void createChallenge(Challenge challenge, Runnable onExpiry) {
        Player challenged = challenge.getChallenged();
        Player challenger = challenge.getChallenger();

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            onExpiry.run();
            challenges.remove(new Challenge(challenged, challenger));
            logger.info("Challenge expired " + challenged.getId() + " " + challenger.getId());
        }, 30, TimeUnit.SECONDS);

        challenges.put(new Challenge(challenged, challenger), future);
    }

    public boolean acceptChallenge(Challenge challenge) {
        Player challenged = challenge.getChallenged();
        Player challenger = challenge.getChallenger();

        ScheduledFuture<?> future = challenges.remove(new Challenge(challenged, challenger));
        if (future != null) {
            future.cancel(false);
            return true;
        }
        return false;
    }
}
