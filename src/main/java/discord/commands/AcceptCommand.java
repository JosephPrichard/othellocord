/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.commands;

import discord.message.GameStartSender;
import discord.renderers.OthelloBoardRenderer;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import services.Challenge;
import services.ChallengeService;
import services.GameService;
import services.Player;
import services.exceptions.AlreadyPlayingException;

import static utils.Logger.LOGGER;

public class AcceptCommand extends Command
{
    private final GameService gameService;
    private final ChallengeService challengeService;
    private final OthelloBoardRenderer boardRenderer;

    public AcceptCommand(
        GameService gameService,
        ChallengeService challengeService,
        OthelloBoardRenderer boardRenderer
    ) {
        super("accept", "Accepts a challenge from another discord user",
            new OptionData(OptionType.USER, "challenger", "User who made the challenge", true));
        this.gameService = gameService;
        this.challengeService = challengeService;
        this.boardRenderer = boardRenderer;
    }

    @Override
    public void doCommand(CommandContext ctx) {
        var opponentUser = ctx.getParam("challenger").getAsUser();
        var opponent = new Player(opponentUser);
        var player = new Player(ctx.getAuthor());

        if (!challengeService.acceptChallenge(new Challenge(player, opponent))) {
            ctx.reply("No challenge to accept.");
            return;
        }

        try {
            var game = gameService.createGame(player, opponent);
            var image = boardRenderer.drawBoard(game.getBoard());

            var sender = new GameStartSender()
                .setGame(game)
                .setTag(game)
                .setImage(image);
            sender.sendReply(ctx);
        } catch(AlreadyPlayingException ex) {
            ctx.reply("One ore more players are already in a game.");
        }

        LOGGER.info("Player " + player + " accepted challenge from " + opponent);
    }
}
