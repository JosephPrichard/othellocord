/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.stats;

import services.game.GameResult;

// writes stats to an external service
public interface StatsWriter {

    StatsResult writeStats(GameResult result);
}
