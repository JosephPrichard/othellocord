/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.stats;

import services.player.Player;

import java.util.List;

public interface StatsReader {
    Stats getStats(Player player);

    List<Stats> getTopStats();
}
