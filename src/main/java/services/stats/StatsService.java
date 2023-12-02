/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.stats;

import services.game.GameResult;
import services.game.Player;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

import static utils.Logger.LOGGER;

public class StatsService implements StatsMutator {
    public static final int K = 30;
    private final StatsDao statsDao;
    private final StatsMapper mapper;
    private final ExecutorService es;

    public StatsService(StatsDao statsDao, UserFetcher userFetcher, ExecutorService es) {
        this.statsDao = statsDao;
        this.mapper = new StatsMapper(userFetcher);
        this.es = es;
    }

    public Stats getStats(Player player) {
        var statsEntity = statsDao.getOrSaveStats(player.getId());
        return mapper.map(statsEntity);
    }

    public List<Stats> getTopStats() {
        var statsEntityList = statsDao.getTopStats(25);
        return mapper.mapAll(statsEntityList);
    }


    public static float calcProbability(float rating1, float rating2) {
        return 1.0f / (1.0f + ((float) Math.pow(10, (rating1 - rating2) / 400f)));
    }

    public static float calcEloWon(float rating, float probability) {
        return rating + K * (1f - probability);
    }

    public static float calcEloLost(float rating, float probability) {
        return rating - K * probability;
    }

    public void updateStats(GameResult result) {
        // retrieve the stats for the winner by submitting both to the thread pool and waiting for a response
        var winnerFuture = es.submit(() -> statsDao.getOrSaveStats(result.getWinner().getId()));
        var loserFuture = es.submit(() -> statsDao.getOrSaveStats(result.getLoser().getId()));

        StatsEntity winnerStats;
        StatsEntity loserStats;
        try {
            winnerStats = winnerFuture.get();
            loserStats = loserFuture.get();
        } catch (ExecutionException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "Failed to retrieve the stats when performing the update stats operation");
            return;
        }

        if (result.isDraw() || result.getWinner().equals(result.getLoser())) {
            // draw games don't need to update the elo, nor do games against self
            result.setElo(winnerStats.getElo(), loserStats.getElo());
            result.setEloDiff(0, 0);
            return;
        }

        // perform elo calculations
        var winnerEloBefore = winnerStats.getElo();
        var loserEloBefore = loserStats.getElo();
        var probWin = calcProbability(loserStats.getElo(), winnerStats.getElo());
        var probLost = calcProbability(winnerStats.getElo(), loserStats.getElo());
        var winnerEloAfter = calcEloWon(winnerStats.getElo(), probWin);
        var loserEloAfter = calcEloLost(loserStats.getElo(), probLost);

        // set new values in entities
        winnerStats.setElo(winnerEloAfter);
        loserStats.setElo(loserEloAfter);
        winnerStats.setWon(winnerStats.getWon() + 1);
        loserStats.setLost(loserStats.getLost() + 1);

        // update stats in dao
        statsDao.updateStats(winnerStats, loserStats);

        // set the changed values for the result object
        result.setElo(winnerStats.getElo(), loserStats.getElo());
        result.setEloDiff(winnerEloAfter - winnerEloBefore, loserEloAfter - loserEloBefore);
    }
}
