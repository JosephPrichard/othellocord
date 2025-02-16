/*
 * Copyright (c) Joseph Prichard 2024.
 */

package services;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;

import java.util.concurrent.CompletableFuture;

import static utils.LogUtils.LOGGER;

// functional interface that fetches user data from an external service
public interface UserFetcher {

    // default implementation that creates a function to fetch a username from jda
    static UserFetcher usingDiscord(JDA jda) {
        return (id) -> jda
            .retrieveUserById(id)
            .submit()
            .thenApply(User::getName)
            .exceptionally(ex -> {
                LOGGER.error("Failed to load username for a player id from jda {}", id);
                return "Unknown User";
            });
    }

    CompletableFuture<String> fetchUsername(Long longId);
}
