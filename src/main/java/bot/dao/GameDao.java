package bot.dao;

import bot.DataSource;
import bot.entities.GameEntity;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.query.Query;

import javax.annotation.Nullable;
import java.util.List;

public class GameDao
{
    private final DataSource dataSource;

    public GameDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void saveGame(Long blackPlayerId, Long whitePlayerId, String board) {
        Session session = dataSource.getSession();
        Transaction transaction = session.beginTransaction();

        GameEntity game = new GameEntity();
        game.setWhitePlayerId(whitePlayerId);
        game.setBlackPlayerId(blackPlayerId);
        game.setBoard(board);

        session.save(game);
        transaction.commit();
        session.close();
    }

    @Nullable
    public GameEntity getGame(Long playerId) {
        Session session = dataSource.getSession();

        String str = "from GameEntity where whitePlayerId = :id or blackPlayerId = :id";
        Query<GameEntity> query = session.createQuery(str, GameEntity.class);
        query.setParameter("id", playerId);

        List<GameEntity> games = query.list();
        GameEntity game = games.size() == 1 ? games.get(0) : null;

        session.close();
        return game;
    }

    public void updateGame(Long playerId, String board) {
        Session session = dataSource.getSession();
        Transaction transaction = session.beginTransaction();

        String str = "update GameEntity set board = :board where whitePlayerId = :id or blackPlayerId = :id ";
        Query<?> query = session.createQuery(str);
        query.setParameter("id", playerId);
        query.setParameter("board", board);

        query.executeUpdate();
        transaction.commit();
        session.close();
    }

    public void deleteGame(Long playerId) {
        Session session = dataSource.getSession();
        Transaction transaction = session.beginTransaction();

        String str = "delete from GameEntity where whitePlayerId = :id or blackPlayerId = :id";
        Query<?> query = session.createQuery(str);
        query.setParameter("id", playerId);

        query.executeUpdate();
        transaction.commit();
        session.close();
    }

    public static void main(String[] args) {
        GameDao gameDao = new GameDao(new DataSource());

        gameDao.saveGame(0L, 1L, "test");
        System.out.println(gameDao.getGame(0L));

        gameDao.updateGame(1L, "test1");
        System.out.println(gameDao.getGame(0L));

        gameDao.deleteGame(0L);
        System.out.println(gameDao.getGame(0L));
    }
}
