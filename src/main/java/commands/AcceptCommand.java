/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import messaging.senders.GameStartSender;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import othello.BoardRenderer;
import services.challenge.Challenge;
import services.challenge.ChallengeScheduler;
import services.game.GameStorage;
import services.player.Player;
import services.game.exceptions.AlreadyPlayingException;

import static utils.Logger.LOGGER;

public class AcceptCommand extends Command {
    private final GameStorage gameStorage;
    private final ChallengeScheduler challengeScheduler;
    private final BoardRenderer boardRenderer;

    public AcceptCommand(
        GameStorage gameStorage,
        ChallengeScheduler challengeScheduler,
        BoardRenderer boardRenderer
    ) {
        super("accept", "Accepts a challenge from another discord user",
            new OptionData(OptionType.USER, "challenger", "User who made the challenge", true));
        this.gameStorage = gameStorage;
        this.challengeScheduler = challengeScheduler;
        this.boardRenderer = boardRenderer;
    }

    @Override
    public void doCommand(CommandContext ctx) {
        var opponentUser = ctx.getParam("challenger").getAsUser();
        var opponent = new Player(opponentUser);
        var player = new Player(ctx.getAuthor());

        if (!challengeScheduler.acceptChallenge(new Challenge(player, opponent))) {
            ctx.reply("No challenge to accept.");
            return;
        }

        try {
            var game = gameStorage.createGame(player, opponent);
            var image = boardRenderer.drawBoard(game.board());

            var sender = new GameStartSender()
                .setGame(game)
                .setTag(game)
                .setImage(image);
            sender.sendReply(ctx);
        } catch (AlreadyPlayingException ex) {
            ctx.reply("One ore more players are already in a game.");
        }

        LOGGER.info("Player " + player + " accepted challenge from " + opponent);
    }
}
