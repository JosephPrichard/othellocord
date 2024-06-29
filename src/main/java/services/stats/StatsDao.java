/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.stats;

import services.DataSource;

import javax.annotation.Nullable;
import java.util.List;

// implementation that uses hibernate orm to persist stats entities to a database
public class StatsDao implements IStatsDao {

    private final DataSource dataSource;

    public StatsDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public StatsEntity saveStats(Long playerId) {
        var session = dataSource.getSession();
        var transaction = session.beginTransaction();

        var stats = new StatsEntity();
        stats.setPlayerId(playerId);
        stats.setElo(1000f);
        stats.setWon(0);
        stats.setLost(0);
        stats.setDrawn(0);

        session.save(stats);
        transaction.commit();
        session.close();

        return stats;
    }

    @Nullable
    public StatsEntity getStats(Long playerId) {
        var session = dataSource.getSession();
        var stats = session.get(StatsEntity.class, playerId);
        session.close();
        return stats;
    }

    public StatsEntity getOrSaveStats(Long playerId) {
        var statsEntity = getStats(playerId);
        if (statsEntity == null) {
            statsEntity = saveStats(playerId);
        }
        return statsEntity;
    }

    public List<StatsEntity> getTopStats(int amount) {
        var session = dataSource.getSession();

        var str = "from StatsEntity order by elo desc";
        var query = session.createQuery(str, StatsEntity.class);
        query.setMaxResults(amount);

        var stats = query.list();
        session.close();
        return stats;
    }

    public void updateStats(StatsEntity... stats) {
        var session = dataSource.getSession();
        var transaction = session.beginTransaction();

        for (var s : stats) {
            session.update(s);
        }

        transaction.commit();
        session.close();
    }

    public void deleteStats(Long playerId) {
        var session = dataSource.getSession();
        var transaction = session.beginTransaction();

        var stats = session.get(StatsEntity.class, playerId);

        session.delete(stats);
        transaction.commit();
        session.close();
    }

    public static void main(String[] args) {
        var statsDao = new StatsDao(new DataSource());

        statsDao.saveStats(0L);
        System.out.println(statsDao.getStats(0L));

        System.out.println(statsDao.getTopStats(10));

        var stats = new StatsEntity();
        stats.setPlayerId(0L);
        stats.setElo(1015f);
        stats.setWon(1);
        statsDao.updateStats(stats);
        System.out.println(statsDao.getStats(0L));

        statsDao.deleteStats(0L);
        System.out.println(statsDao.getStats(0L));
    }
}
