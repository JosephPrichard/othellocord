/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.challenge;

// responsible for managing stored challenges and events
public interface IChallengeScheduler {

    void createChallenge(Challenge challenge, Runnable onExpiry);

    boolean acceptChallenge(Challenge challenge);
}
