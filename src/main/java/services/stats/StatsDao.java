/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.stats;

import javax.annotation.Nullable;
import java.util.List;

public interface StatsDao {

    StatsEntity saveStats(Long playerId);

    @Nullable
    StatsEntity getStats(Long playerId);

    StatsEntity getOrSaveStats(Long playerId);

    List<StatsEntity> getTopStats(int amount);

    void updateStats(StatsEntity... stats);

    void deleteStats(Long playerId);
}
