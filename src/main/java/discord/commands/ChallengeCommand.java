/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.commands;

import discord.JDASingleton;
import services.Challenge;
import services.ChallengeService;
import services.Player;
import discord.message.ChallengeBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.logging.Logger;

public class ChallengeCommand extends Command
{
    private final Logger logger = Logger.getLogger("command.challenge");
    private final ChallengeService challengeService;

    public ChallengeCommand(ChallengeService challengeService) {
        super("challenge", "Challenges an another discord user to an Othello game", "opponent");
        this.challengeService = challengeService;
    }

    @Override
    public void doCommand(CommandContext ctx) {
        var event = ctx.getEvent();
        var channel = event.getChannel();

        var opponentUser = JDASingleton.fetchUserFromDirect(ctx.getParam("opponent"));
        if (opponentUser == null) {
            channel.sendMessage("Can't find a discord user with that id. Try using @ directly.").queue();
            return;
        }

        var opponent = new Player(opponentUser);
        var player = new Player(event.getAuthor());

        var id = player.getId();
        Runnable onExpiry = () -> channel.sendMessage("<@" + id + "> Challenge timed out!").queue();
        challengeService.createChallenge(new Challenge(opponent, player), onExpiry);

        var message = new ChallengeBuilder()
            .setChallenged(opponent)
            .setChallenger(player)
            .build();

        channel.sendMessage(message).queue();

        logger.info("Player " + player + " challenged opponent " + opponent);
    }
}
