package bot.services;

import bot.dao.StatsDao;
import bot.dtos.GameResultDto;
import bot.dtos.PlayerDto;
import bot.dtos.StatsDto;
import bot.entities.StatsEntity;
import bot.mappers.StatsDtoMapper;
import bot.utils.EloUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StatsService
{
    private final StatsDao statsDao;
    private final StatsDtoMapper dtoMapper = new StatsDtoMapper();

    public StatsService(StatsDao statsDao) {
        this.statsDao = statsDao;
    }

    public StatsDto getStats(PlayerDto player) {
        StatsEntity statsEntity = statsDao.getOrSaveStats(player.getId());
        return dtoMapper.map(statsEntity);
    }

    public List<StatsDto> getTopStats() {
        List<StatsEntity> statsEntityList = statsDao.getTopStats(25);
        return dtoMapper.mapAll(statsEntityList);
    }

    public void updateStats(GameResultDto result) {
        // retrieve the stats for the winner and the loser in parallel
        Supplier<StatsEntity> getWinner = () -> statsDao.getOrSaveStats(result.getWinner().getId());
        Supplier<StatsEntity> getLoser = () -> statsDao.getOrSaveStats(result.getLoser().getId());
        List<StatsEntity> entities = Stream.of(getWinner, getLoser)
            .parallel()
            .map(Supplier::get)
            .collect(Collectors.toList());

        StatsEntity winnerStats = entities.get(0);
        StatsEntity loserStats = entities.get(1);

        if (result.isDraw() || result.getWinner().equals(result.getLoser())) {
            // draw games don't need to update the elo, nor do games against self
            result.setElo(winnerStats.getElo(), loserStats.getElo());
            result.setEloDiff(0, 0);
            return;
        }

        // perform elo calculations
        float winnerEloBefore = winnerStats.getElo();
        float loserEloBefore = loserStats.getElo();
        float probWin = EloUtils.probability(loserStats.getElo(), winnerStats.getElo());
        float probLost = EloUtils.probability(winnerStats.getElo(), loserStats.getElo());
        float winnerEloAfter = EloUtils.ratingWon(winnerStats.getElo(), probWin);
        float loserEloAfter = EloUtils.ratingLost(loserStats.getElo(), probLost);

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
