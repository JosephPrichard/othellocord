/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.commands;

import discord.JDASingleton;
import discord.commands.abstracts.CommandContext;
import discord.commands.abstracts.Command;
import services.challenge.Challenge;
import services.challenge.ChallengeService;
import services.player.Player;
import discord.message.builder.ChallengeBuilder;
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
        MessageReceivedEvent event = ctx.getEvent();
        MessageChannel channel = event.getChannel();

        User opponentUser = JDASingleton.fetchUserFromDirect(ctx.getParam("opponent"));
        if (opponentUser == null) {
            channel.sendMessage("Can't find a discord user with that id. Try using @ directly.").queue();
            return;
        }

        Player opponent = new Player(opponentUser);
        Player player = new Player(event.getAuthor());

        long id = player.getId();
        Runnable onExpiry = () -> channel.sendMessage("<@" + id + "> Challenge timed out!").queue();
        challengeService.createChallenge(new Challenge(opponent, player), onExpiry);

        String message = new ChallengeBuilder()
            .setChallenged(opponent)
            .setChallenger(player)
            .build();

        channel.sendMessage(message).queue();

        logger.info("Player " + player + " challenged opponent " + opponent);
    }
}
