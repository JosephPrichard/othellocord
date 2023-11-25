/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord;

import utils.Discord;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;

import javax.annotation.Nullable;
import javax.security.auth.login.LoginException;

public class JDASingleton
{
    private static JDASingleton instance = null;

    private final JDA jda;

    private JDASingleton(JDA jda) {
        this.jda = jda;
    }

    public static void initJDASingleton(String token) throws LoginException {
        var jda = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES)
            .addEventListeners(new OthelloBot())
            .setActivity(Activity.playing("Othello"))
            .build();
        instance = new JDASingleton(jda);
    }

    public static JDASingleton getInstance() {
        return instance;
    }

    public JDA getJda() {
        return jda;
    }

    @Nullable
    public static User fetchUserFromDirect(String id) {
        try {
            var longId = Discord.toLongId(id);
            return getInstance().jda.retrieveUserById(longId).complete();
        } catch(NumberFormatException ex) {
            return null;
        }
    }

    public static RestAction<User> fetchUser(Long longId) {
        return getInstance().jda.retrieveUserById(longId);
    }
}
