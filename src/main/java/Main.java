/*
 * Copyright (c) Joseph Prichard 2023.
 */

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import utils.ConfigUtils;

import javax.security.auth.login.LoginException;

import static utils.LogUtils.LOGGER;

public class Main {

    public static void main(String[] args) throws LoginException {
        var envFile = Main.class.getResourceAsStream(".env");
        var botToken = ConfigUtils.readJDAToken(envFile);

        System.out.println("Token: " + botToken);

        LOGGER.info("Starting the bot");
        var bot = new OthelloBot();

        var jda = JDABuilder.createLight(botToken, GatewayIntent.GUILD_MESSAGES)
            .addEventListeners(bot)
            .setActivity(Activity.playing("Othello"))
            .build();
        bot.init(jda);
    }
}