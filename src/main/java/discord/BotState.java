/*
 * Copyright (c) Joseph Prichard 2024.
 */

package discord;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.JDA;
import services.*;

import java.util.concurrent.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BotState {
    public static final int CORES = Runtime.getRuntime().availableProcessors();

    private UserFetcher userFetcher;
    private AgentDispatcher agentDispatcher;
    private StatsService statsService;
    private GameService gameService;
    private ChallengeScheduler challengeScheduler;
    private ScheduledExecutorService scheduler;
    private ExecutorService taskExecutor;

    private static ThreadFactory createThreadFactory(String pool) {
        return (task) -> {
            Thread thread = new Thread(task, pool);
            thread.setDaemon(true);
            return thread;
        };
    }

    public BotState(JDA jda) {
        DataSource dataSource = new DataSource();
        ThreadPoolExecutor cpuBndExecutor = new ThreadPoolExecutor(CORES / 2, CORES / 2,
            0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), createThreadFactory("CPU-Bnd-Pool"));
        StatsDao statsDao = new StatsDao(dataSource);

        taskExecutor = Executors.newCachedThreadPool(createThreadFactory("Task-Pool"));
        scheduler = Executors.newScheduledThreadPool(1, createThreadFactory("Schedule-Pool"));
        userFetcher = UserFetcher.usingDiscord(jda);
        agentDispatcher = new AgentDispatcher(cpuBndExecutor);
        statsService = new StatsService(statsDao, userFetcher);
        gameService = new GameService(statsService);
        challengeScheduler = new ChallengeScheduler();
    }
}
