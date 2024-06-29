/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.stats;

import services.game.GameResult;
import services.player.Player;
import services.player.UserFetcher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// implementation that delegates persistence to a data access object and performs calculations, mapping, and flow control
// depends on blocking io dao class and therefore also uses blocking io
public class StatsService implements IStatsService {

    public static final int ELO_K = 30;
    private final IStatsDao statsDao;
    private final UserFetcher userFetcher;

    public StatsService(IStatsDao statsDao, UserFetcher userFetcher) {
        this.statsDao = statsDao;
        this.userFetcher = userFetcher;
    }

    public Stats readStats(Player player) {
        var statsEntity = statsDao.getOrSaveStats(player.id());
        // we assume the tag can be loaded, so we throw an exception if it cannot be read
        var tag = userFetcher.fetchUsername(statsEntity.getPlayerId()).join();
        return new Stats(statsEntity, tag);
    }

    public List<Stats> readTopStats() {
        var statsEntityList = statsDao.getTopStats(25);

        // fetch each tag and wait til each fetch operation is complete
        var futures = statsEntityList
            .stream()
            .map((entity) ->
                Player.Bot.isBotId(entity.getPlayerId())
                    ? CompletableFuture.<String>completedFuture(null)
                    : userFetcher.fetchUsername(entity.getPlayerId())
            )
            .toList();

        List<Stats> statsList = new ArrayList<>();
        for (int i = 0; i < statsEntityList.size(); i++) {
            var statsEntity = statsEntityList.get(i);
            var future = futures.get(i);

            var tag = future.join();
            if (tag == null) {
                tag = Player.Bot.name(statsEntity.getPlayerId());
            }

            statsList.add(new Stats(statsEntity, tag));
        }

        return statsList;
    }

    public static float calcProbability(float rating1, float rating2) {
        return 1.0f / (1.0f + ((float) Math.pow(10, (rating1 - rating2) / 400f)));
    }

    public static float calcEloWon(float rating, float probability) {
        return rating + ELO_K * (1f - probability);
    }

    public static float calcEloLost(float rating, float probability) {
        return rating - ELO_K * probability;
    }

    public StatsResult writeStats(GameResult result) {
        StatsEntity winnerStats = statsDao.getOrSaveStats(result.winner().id());
        StatsEntity loserStats = statsDao.getOrSaveStats(result.loser().id());

        if (result.isDraw() || result.winner().equals(result.loser())) {
            // draw games don't need to update the elo, nor do games against self
            return new StatsResult(winnerStats.getElo(), loserStats.getElo(), 0, 0);
        }

        // perform elo calculations
        var winnerEloBefore = winnerStats.getElo();
        var loserEloBefore = loserStats.getElo();
        var probWin = calcProbability(loserStats.getElo(), winnerStats.getElo());
        var probLost = calcProbability(winnerStats.getElo(), loserStats.getElo());
        var winnerEloAfter = calcEloWon(winnerStats.getElo(), probWin);
        var loserEloAfter = calcEloLost(loserStats.getElo(), probLost);
        var winnerEloDiff = winnerEloAfter - winnerEloBefore;
        var loserEloDiff = loserEloAfter - loserEloBefore;

        // set new values in entities
        winnerStats.setElo(winnerEloAfter);
        loserStats.setElo(loserEloAfter);
        winnerStats.setWon(winnerStats.getWon() + 1);
        loserStats.setLost(loserStats.getLost() + 1);

        // update stats in dao
        statsDao.updateStats(winnerStats, loserStats);

        return new StatsResult(winnerStats.getElo(), loserStats.getElo(), winnerEloDiff, loserEloDiff);
    }
}
