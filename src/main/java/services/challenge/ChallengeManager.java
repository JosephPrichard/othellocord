/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.challenge;

public interface ChallengeManager {
    void createChallenge(Challenge challenge, Runnable onExpiry);

    boolean acceptChallenge(Challenge challenge);
}
