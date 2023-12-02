/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.stats;

import services.game.GameResult;

public interface StatsMutator {
    void updateStats(GameResult result);
}
