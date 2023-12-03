/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.player;

import java.util.concurrent.CompletableFuture;

public interface UserFetcher {

    CompletableFuture<String> fetchUserTag(Long longId);
}
