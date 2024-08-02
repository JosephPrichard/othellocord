/*
 * Copyright (c) Joseph Prichard 2024.
 */

package services;

import models.StatsEntity;

import javax.annotation.Nullable;
import java.util.List;

public class StatsDao {

    private final DataSource dataSource;

    public StatsDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public StatsEntity saveStats(Long playerId) {
        var session = dataSource.getSession();
        var transaction = session.beginTransaction();

        var stats = new StatsEntity();
        stats.playerId = playerId;
        stats.elo = 1000f;
        stats.won = 0;
        stats.lost = 0;
        stats.drawn = 0;

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
        stats.playerId = 0L;
        stats.elo = 1015f;
        stats.won = 1;
        statsDao.updateStats(stats);
        System.out.println(statsDao.getStats(0L));

        statsDao.deleteStats(0L);
        System.out.println(statsDao.getStats(0L));
    }
}
