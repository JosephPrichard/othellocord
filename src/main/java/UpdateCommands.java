/*
 * Copyright (c) Joseph Prichard 2024.
 */

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import utils.ConfigUtils;

import javax.security.auth.login.LoginException;

import java.io.InputStream;

import static utils.LogUtils.LOGGER;

public class UpdateCommands {

    public static void main(String[] args) throws LoginException, InterruptedException {
        InputStream envFile = Main.class.getResourceAsStream(".env");
        String botToken = ConfigUtils.readJDAToken(envFile);

        JDA jda = JDABuilder.createLight(botToken, GatewayIntent.GUILD_MESSAGES)
            .addEventListeners(new OthelloBot())
            .setActivity(Activity.playing("Othello"))
            .build();

        LOGGER.info("Updating the commands to discord");
        jda.updateCommands()
            .addCommands(OthelloBot.getCommandData())
            .complete();
        LOGGER.info("Finished updating the commands to discord");

        jda.awaitReady();
        jda.shutdown();
    }
}
