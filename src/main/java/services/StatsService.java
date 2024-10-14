/*
 * Copyright (c) Joseph Prichard 2024.
 */

package services;

import lombok.AllArgsConstructor;
import models.Game;
import models.Player;
import models.Stats;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static utils.LogUtils.LOGGER;

@AllArgsConstructor
public class StatsService {

    public static final int ELO_K = 30;
    private final StatsDao statsDao;
    private final UserFetcher userFetcher;

    public Stats readStats(Player player) {
        var statsEntity = statsDao.getOrSaveStats(player.id);
        // we assume the tag can be loaded, so we throw an exception if it cannot be read
        var tag = userFetcher.fetchUsername(statsEntity.playerId).join();
        return new Stats(statsEntity, tag);
    }

    public List<Stats> readTopStats() {
        var statsEntityList = statsDao.getTopStats(25);

        // fetch each tag and wait til each fetch operation is complete
        var futures = statsEntityList
            .stream()
            .map((entity) -> Player.Bot.isBotId(entity.playerId)
                ? CompletableFuture.<String>completedFuture(null) : userFetcher.fetchUsername(entity.playerId))
            .toList();

        List<Stats> statsList = new ArrayList<>();
        for (int i = 0; i < statsEntityList.size(); i++) {
            var statsEntity = statsEntityList.get(i);
            var future = futures.get(i);

            var tag = future.join();
            if (tag == null) {
                tag = Player.Bot.name(statsEntity.playerId);
            }

            statsList.add(new Stats(statsEntity, tag));
        }

        return statsList;
    }

    public static float probability(float rating1, float rating2) {
        return 1.0f / (1.0f + ((float) Math.pow(10, (rating1 - rating2) / 400f)));
    }

    public static float eloWon(float rating, float probability) {
        return rating + ELO_K * (1f - probability);
    }

    public static float eloLost(float rating, float probability) {
        return rating - ELO_K * probability;
    }

    public Stats.Result writeStats(Game.Result result) {
        var win = statsDao.getOrSaveStats(result.winner().id);
        var loss = statsDao.getOrSaveStats(result.loser().id);

        if (result.isDraw() || result.winner().equals(result.loser())) {
            // draw games don't need to update the elo, nor do games against self
            var stats = new Stats.Result(win.elo, loss.elo, 0, 0);
            LOGGER.info("Wrote stats with result: {}", stats);
            return stats;
        }

        var winEloBefore = win.elo;
        var lossEloBefore = loss.elo;
        win.elo = eloWon(win.elo, probability(loss.elo, win.elo));
        loss.elo = eloLost(loss.elo, probability(win.elo, loss.elo));
        win.won += 1;
        loss.lost += 1;

        statsDao.updateStats(win, loss);

        var winDiff = win.elo - winEloBefore;
        var lossDiff = loss.elo - lossEloBefore;

        var stats = new Stats.Result(win.elo, loss.elo, winDiff, lossDiff);
        LOGGER.info("Wrote stats with result: {}", stats);
        return stats;
    }
}
