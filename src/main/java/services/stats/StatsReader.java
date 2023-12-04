/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.stats;

import services.player.Player;

import java.util.List;

// reads stats from an external service
public interface StatsReader {

    Stats readStats(Player player);

    List<Stats> readTopStats();
}
