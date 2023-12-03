/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import services.game.GameResult;
import services.player.Player;
import services.player.UserFetcher;
import services.stats.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.mockito.Mockito.*;

public class TestStatsService {

    private StatsDao mock_statsDao;
    private UserFetcher mock_userFetcher;
    private StatsService statsService;

    @BeforeEach
    public void beforeEach() {
        mock_statsDao = mock(StatsDao.class);
        mock_userFetcher = mock(UserFetcher.class);
        var es = Executors.newSingleThreadExecutor();
        statsService = new StatsService(mock_statsDao, mock_userFetcher, es);
    }

    @Test
    public void whenGetStats_success() {
        var player = new Player(1000);

        when(mock_statsDao.getOrSaveStats(1000L))
            .thenReturn(new StatsEntity(1000L));
        when(mock_userFetcher.fetchUserTag(1000L))
            .thenReturn(CompletableFuture.completedFuture("Player1"));

        var stats = statsService.getStats(player);

        Assertions.assertEquals(new Stats(new Player(1000, "Player1")), stats);
    }

    @Test
    public void whenGetTopStats_success() {
        when(mock_statsDao.getTopStats(anyInt()))
            .thenReturn(List.of(
                new StatsEntity(1000L),
                new StatsEntity(1001L),
                new StatsEntity(1002L)
            ));
        when(mock_userFetcher.fetchUserTag(1000L))
            .thenReturn(CompletableFuture.completedFuture("Player1"));
        when(mock_userFetcher.fetchUserTag(1001L))
            .thenReturn(CompletableFuture.completedFuture("Player2"));
        when(mock_userFetcher.fetchUserTag(1002L))
            .thenReturn(CompletableFuture.completedFuture("Player3"));

        var statsList = statsService.getTopStats();

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
        var result = GameResult.WinLoss(winner, loser);

        when(mock_statsDao.getOrSaveStats(1000L))
            .thenReturn(new StatsEntity(1000L, 1015f, 1, 1, 1));
        when(mock_statsDao.getOrSaveStats(1001L))
            .thenReturn(new StatsEntity(1001L, 1015f, 1, 1, 1));

        statsService.updateStats(result);

        verify(mock_statsDao).updateStats(
            argThat((arg) -> arg.getWon() == 2 && arg.getLost() == 1),
            argThat((arg) -> arg.getWon() == 1 && arg.getLost() == 2));

        Assertions.assertEquals(1015f, result.getWinnerElo() - result.getWinnerEloDiff());
        Assertions.assertEquals(1015f, result.getLoserElo() - result.getLoserEloDiff());
        Assertions.assertTrue(result.getWinnerEloDiff() > 0f);
        Assertions.assertTrue(result.getLoserEloDiff() < 0f);
    }

    @Test
    public void whenUpdateStats_ifDraw_correctResult() {
        var winner = new Player(1000, "Player1");
        var loser = new Player(1001, "Player2");
        var result = GameResult.Draw(winner, loser);

        when(mock_statsDao.getOrSaveStats(1000L))
            .thenReturn(new StatsEntity(1000L, 1015f, 1, 1, 1));
        when(mock_statsDao.getOrSaveStats(1001L))
            .thenReturn(new StatsEntity(1001L, 1015f, 1, 1, 1));

        statsService.updateStats(result);

        Assertions.assertEquals(1015f, result.getWinnerElo());
        Assertions.assertEquals(1015f, result.getLoserElo());
        Assertions.assertEquals(0f, result.getWinnerEloDiff());
        Assertions.assertEquals(0f, result.getLoserEloDiff());
    }
}
