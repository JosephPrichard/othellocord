/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.player;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import services.player.exceptions.UnknownUserException;

import java.util.concurrent.CompletableFuture;

// functional interface that fetches user data from an external service
public interface UserFetcher {

    static UserFetcher discordFetcher(JDA jda) {
        return (id) -> {
            try {
                return jda.retrieveUserById(id)
                    .submit()
                    .thenApply(User::getAsTag);
            } catch (ErrorResponseException ex) {
                throw new UnknownUserException();
            }
        };
    }

    CompletableFuture<String> fetchUserTag(Long longId);
}
