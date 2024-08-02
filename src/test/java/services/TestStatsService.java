/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services;

import models.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

public class TestStatsService {

    private StatsDao mock_statsDao;
    private UserFetcher mock_userFetcher;
    private StatsService statsService;

    @BeforeEach
    public void beforeEach() {
        mock_statsDao = mock(StatsDao.class);
        mock_userFetcher = mock(UserFetcher.class);
        statsService = new StatsService(mock_statsDao, mock_userFetcher);
    }

    @Test
    public void whenReadStats_success() {
        var player = new Player(1000);

        when(mock_statsDao.getOrSaveStats(1000L))
            .thenReturn(new StatsEntity(1000L));
        when(mock_userFetcher.fetchUsername(1000L))
            .thenReturn(CompletableFuture.completedFuture("Player1"));

        var stats = statsService.readStats(player);

        Assertions.assertEquals(new Stats(new Player(1000, "Player1")), stats);
    }

    @Test
    public void whenReadTopStats_success() {
        when(mock_statsDao.getTopStats(anyInt()))
            .thenReturn(List.of(
                new StatsEntity(1000L),
                new StatsEntity(1001L),
                new StatsEntity(1002L)
            ));
        when(mock_userFetcher.fetchUsername(1000L))
            .thenReturn(CompletableFuture.completedFuture("Player1"));
        when(mock_userFetcher.fetchUsername(1001L))
            .thenReturn(CompletableFuture.completedFuture("Player2"));
        when(mock_userFetcher.fetchUsername(1002L))
            .thenReturn(CompletableFuture.completedFuture("Player3"));

        var statsList = statsService.readTopStats();

        Assertions.assertEquals(List.of(
            new Stats(new Player(1000L, "Player1")),
            new Stats(new Player(1001L, "Player2")),
            new Stats(new Player(1002L, "Player3"))
        ), statsList);
    }

    @Test
    public void whenUpdateStats_correctResult() {
        var winner = new Player(1000, "Player1");
        var loser = new Player(1001, "Player2");
        var result = Game.Result.WinLoss(winner, loser);

        when(mock_statsDao.getOrSaveStats(1000L))
            .thenReturn(new StatsEntity(1000L, 1015f, 1, 1, 1));
        when(mock_statsDao.getOrSaveStats(1001L))
            .thenReturn(new StatsEntity(1001L, 1015f, 1, 1, 1));

        var statsRes = statsService.writeStats(result);

        verify(mock_statsDao).updateStats(
            argThat((arg) -> arg.won == 2 && arg.lost == 1),
            argThat((arg) -> arg.won == 1 && arg.lost == 2));

        Assertions.assertEquals(1015f, statsRes.winnerElo() - statsRes.winnerEloDiff());
        Assertions.assertEquals(1015f, statsRes.loserElo() - statsRes.loserEloDiff());
        Assertions.assertTrue(statsRes.winnerEloDiff() > 0f);
        Assertions.assertTrue(statsRes.loserEloDiff() < 0f);
    }

    @Test
    public void whenUpdateStats_ifDraw_correctResult() {
        var winner = new Player(1000, "Player1");
        var loser = new Player(1001, "Player2");
        var result = Game.Result.Draw(winner, loser);

        when(mock_statsDao.getOrSaveStats(1000L))
            .thenReturn(new StatsEntity(1000L, 1015f, 1, 1, 1));
        when(mock_statsDao.getOrSaveStats(1001L))
            .thenReturn(new StatsEntity(1001L, 1015f, 1, 1, 1));

        var statsRes = statsService.writeStats(result);

        Assertions.assertEquals(1015f, statsRes.winnerElo());
        Assertions.assertEquals(1015f, statsRes.loserElo());
        Assertions.assertEquals(0f, statsRes.winnerEloDiff());
        Assertions.assertEquals(0f, statsRes.loserEloDiff());
    }
}
