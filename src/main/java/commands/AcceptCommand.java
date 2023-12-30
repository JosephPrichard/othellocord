/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import commands.messaging.MessageSender;
import othello.BoardRenderer;
import services.challenge.Challenge;
import services.challenge.ChallengeManager;
import services.game.GameStorage;
import services.game.exceptions.AlreadyPlayingException;

import java.util.Objects;

import static utils.Logger.LOGGER;

public class AcceptCommand extends Command {

    private final GameStorage gameStorage;
    private final ChallengeManager challengeManager;

    public AcceptCommand(GameStorage gameStorage, ChallengeManager challengeManager) {
        super("accept");
        this.gameStorage = gameStorage;
        this.challengeManager = challengeManager;
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var opponent = Objects.requireNonNull(ctx.getPlayerParam("challenger"));
        var player = ctx.getPlayer();

        if (!challengeManager.acceptChallenge(new Challenge(player, opponent))) {
            ctx.reply("No challenge to accept.");
            return;
        }

        try {
            var game = gameStorage.createGame(player, opponent);
            var image = BoardRenderer.drawBoard(game.board());

            var sender = MessageSender.createGameStartSender(game, image);
            ctx.replyWithSender(sender);
        } catch (AlreadyPlayingException ex) {
            ctx.reply("One or more players are already in a game.");
        }

        LOGGER.info("Player " + player + " accepted challenge from " + opponent);
    }
}
