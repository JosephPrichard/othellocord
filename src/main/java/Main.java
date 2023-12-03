/*
 * Copyright (c) Joseph Prichard 2023.
 */

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;

import javax.security.auth.login.LoginException;

import static utils.Logger.LOGGER;

public class Main {

    public static void main(String[] args) throws LoginException {
        if (args.length < 1) {
            System.out.println("You have to provide a Discord token as first argument!");
            System.exit(1);
        }

        System.out.println("Token: " + args[0]);

        var token = args[0];

        LOGGER.info("Starting the bot");
        var bot = new OthelloBot();

        var jda = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES)
            .addEventListeners(bot)
            .setActivity(Activity.playing("Othello"))
            .build();
        bot.initListeners(jda);

        LOGGER.info("Updating the commands to discord");
        jda.updateCommands()
            .addCommands(bot.getCommandData())
            .complete();
    }
}