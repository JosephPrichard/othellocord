/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import services.game.GameResult;
import services.game.Player;
import services.stats.StatsDao;
import services.stats.StatsEntity;
import services.stats.StatsService;
import services.stats.UserFetcher;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TestStatsService {

    private StatsDao statsDao;
    private UserFetcher userFetcher;
    private ExecutorService es;
    private StatsService statsService;

    @BeforeEach
    public void beforeEach() {
        statsDao = spy(StatsDao.class);
        userFetcher = mock(UserFetcher.class);
        es = Executors.newFixedThreadPool(1);
        statsService = new StatsService(statsDao, userFetcher, es);
    }

    @Test
    public void testUpdateStats() {
        var winner = new Player(1000, "Player1");
        var loser = new Player(1001, "Player2");
        var result = GameResult.WinLoss(winner, loser);

        when(statsDao.getOrSaveStats(1000L))
            .thenReturn(new StatsEntity(1000L, 1015f, 1, 1, 1));
        when(statsDao.getOrSaveStats(1001L))
            .thenReturn(new StatsEntity(1001L, 1015f, 1, 1, 1));

        statsService.updateStats(result);

        verify(statsDao).updateStats(
            argThat((arg) -> arg.getWon() == 2 && arg.getLost() == 1),
            argThat((arg) -> arg.getWon() == 1 && arg.getLost() == 2));

        Assertions.assertEquals(1015f, result.getWinnerElo() - result.getWinnerEloDiff());
        Assertions.assertEquals(1015f, result.getLoserElo() - result.getLoserEloDiff());
        Assertions.assertTrue(result.getWinnerEloDiff() > 0f);
        Assertions.assertTrue(result.getLoserEloDiff() < 0f);
    }

    @Test
    public void testUpdateStatsDraw() {
        var winner = new Player(1000, "Player1");
        var loser = new Player(1001, "Player2");
        var result = GameResult.Draw(winner, loser);

        when(statsDao.getOrSaveStats(1000L))
            .thenReturn(new StatsEntity(1000L, 1015f, 1, 1, 1));
        when(statsDao.getOrSaveStats(1001L))
            .thenReturn(new StatsEntity(1001L, 1015f, 1, 1, 1));

        statsService.updateStats(result);

        Assertions.assertEquals(1015f, result.getWinnerElo());
        Assertions.assertEquals(1015f, result.getLoserElo());
        Assertions.assertEquals(0f, result.getWinnerEloDiff());
        Assertions.assertEquals(0f, result.getLoserEloDiff());
    }
}
