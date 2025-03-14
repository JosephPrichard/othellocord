/*
 * Copyright (c) Joseph Prichard 2023.
 */

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import utils.ConfigUtils;

import javax.security.auth.login.LoginException;

import java.io.InputStream;

import static utils.LogUtils.LOGGER;

public class Main {

    public static void main(String[] args) throws LoginException {
        InputStream envFile = Main.class.getResourceAsStream(".env");
        String botToken = ConfigUtils.readJDAToken(envFile);

        System.out.println("Token: " + botToken);

        LOGGER.info("Starting the bot");
        OthelloBot bot = new OthelloBot();

        JDA jda = JDABuilder.createLight(botToken, GatewayIntent.GUILD_MESSAGES)
            .addEventListeners(bot)
            .setActivity(Activity.playing("Othello"))
            .build();
        bot.init(jda);
    }
}