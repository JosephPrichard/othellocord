/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.stats;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.RestAction;

public interface UserFetcher {
    RestAction<User> fetchUser(Long longId);
}
