/*
 * Copyright (c) Joseph Prichard 2023.
 */

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;

import javax.security.auth.login.LoginException;

import static utils.Log.LOGGER;

public class Main {

    public static void main(String[] args) throws LoginException {
        var botToken = OthelloBot.readToken();

        System.out.println("Token: " + botToken);

        LOGGER.info("Starting the bot");
        var bot = new OthelloBot();

        var jda = JDABuilder.createLight(botToken, GatewayIntent.GUILD_MESSAGES)
            .addEventListeners(bot)
            .setActivity(Activity.playing("Othello"))
            .build();
        bot.initMessageHandlers(jda);
    }
}