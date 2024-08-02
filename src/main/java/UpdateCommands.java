/*
 * Copyright (c) Joseph Prichard 2024.
 */

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;

import javax.security.auth.login.LoginException;

import static utils.Log.LOGGER;

public class UpdateCommands {

    public static void main(String[] args) throws LoginException, InterruptedException {
        var botToken = OthelloBot.readToken();

        var jda = JDABuilder.createLight(botToken, GatewayIntent.GUILD_MESSAGES)
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
