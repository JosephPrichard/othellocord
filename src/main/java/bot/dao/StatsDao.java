package bot.dao;

import bot.DataSource;
import bot.entities.GameEntity;
import bot.entities.StatsEntity;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import javax.annotation.Nullable;
import java.util.List;

public class StatsDao
{
    private final DataSource dataSource;

    public StatsDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public StatsEntity saveStats(Long playerId) {
        Session session = dataSource.getSession();
        Transaction transaction = session.beginTransaction();

        StatsEntity stats = new StatsEntity();
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
        Session session = dataSource.getSession();
        StatsEntity stats = session.get(StatsEntity.class, playerId);
        session.close();
        return stats;
    }

    public StatsEntity getOrSaveStats(Long playerId) {
        StatsEntity statsEntity = getStats(playerId);
        if (statsEntity == null) {
            statsEntity = saveStats(playerId);
        }
        return statsEntity;
    }

    public List<StatsEntity> getTopStats(int amount) {
        Session session = dataSource.getSession();

        String str = "from StatsEntity order by elo desc";
        Query<StatsEntity> query = session.createQuery(str, StatsEntity.class);
        query.setMaxResults(amount);

        List<StatsEntity> stats = query.list();
        session.close();
        return stats;
    }

    public void updateStats(StatsEntity... stats) {
        Session session = dataSource.getSession();
        Transaction transaction = session.beginTransaction();

        for (StatsEntity s : stats) {
            session.update(s);
        }

        transaction.commit();
        session.close();
    }

    public void deleteStats(Long playerId) {
        Session session = dataSource.getSession();
        Transaction transaction = session.beginTransaction();

        StatsEntity stats = session.get(StatsEntity.class, playerId);

        session.delete(stats);
        transaction.commit();
        session.close();
    }

    public static void main(String[] args) {
        StatsDao statsDao = new StatsDao(new DataSource());

        statsDao.saveStats(0L);
        System.out.println(statsDao.getStats(0L));

        System.out.println(statsDao.getTopStats(10));

        StatsEntity stats = new StatsEntity();
        stats.setPlayerId(0L);
        stats.setElo(1015f);
        stats.setWon(1);
        statsDao.updateStats(stats);
        System.out.println(statsDao.getStats(0L));

        statsDao.deleteStats(0L);
        System.out.println(statsDao.getStats(0L));
    }
}
