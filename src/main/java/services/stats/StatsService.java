/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.stats;

import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import services.game.GameResult;
import services.player.Player;
import services.player.UserFetcher;
import utils.Bot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

import static utils.Logger.LOGGER;

public class StatsService implements StatsMutator {
    public static final int K = 30;
    private final StatsDao statsDao;
    private final ExecutorService es;
    private final UserFetcher userFetcher;
    private final ModelMapper mapper = new ModelMapper();

    public StatsService(StatsDao statsDao, UserFetcher userFetcher, ExecutorService es) {
        this.statsDao = statsDao;
        this.userFetcher = userFetcher;
        this.es = es;
        mapper.typeMap(StatsEntity.class, Stats.class).addMappings(mapper -> {
            Converter<Long, Player> playerConverter = (ctx) -> new Player(ctx.getSource());
            mapper.using(playerConverter).map(StatsEntity::getPlayerId, Stats::setPlayer);
        });
        mapper.validate();
    }

    public Stats getStats(Player player) {
        var statsEntity = statsDao.getOrSaveStats(player.getId());
        var stats = mapper.map(statsEntity, Stats.class);
        try {
            var tag = userFetcher.fetchUserTag(statsEntity.getPlayerId()).get();
            stats.getPlayer().setName(tag);
        } catch(ExecutionException | InterruptedException ex) {
            LOGGER.info("Failed to load the tag name for mapped player " + stats.getPlayer());
        }
        return stats;
    }

    public List<Stats> getTopStats() {
        var statsEntityList = statsDao.getTopStats(25);
        // fetch each tag from jda using futures, for the bots return null and map bot name instead
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (var entity : statsEntityList) {
            var future = Bot.isBotId(entity.getPlayerId()) ?
                CompletableFuture.<String>completedFuture(null) :
                userFetcher.fetchUserTag(entity.getPlayerId());
            futures.add(future);
        }
        CompletableFuture.allOf((futures.toArray(new CompletableFuture[0]))).join();

        // map each entity to dto
        List<Stats> statsList = new ArrayList<>();
        for (var i = 0; i < futures.size(); i++) {
            // retrieve tag from completed future
            var tag = futures.get(i).join();
            if (tag == null) {
                tag = Bot.getBotName(statsEntityList.get(i).getPlayerId());
            }
            // map entity to dto and add to dto list
            var stats = mapper.map(statsEntityList.get(i), Stats.class);
            stats.getPlayer().setName(tag);
            statsList.add(stats);
        }
        return statsList;
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
