/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.stats;

import services.game.GameResult;

public interface StatsWriter {

    void updateStats(GameResult result);
}
