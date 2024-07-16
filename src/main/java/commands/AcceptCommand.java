/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import commands.views.GameStateView;
import othello.BoardRenderer;
import services.challenge.Challenge;
import services.challenge.IChallengeScheduler;
import services.game.IGameService;
import services.game.exceptions.AlreadyPlayingException;

import java.util.Objects;

import static utils.Logger.LOGGER;

public class AcceptCommand extends Command {

    private final IGameService gameService;
    private final IChallengeScheduler challengeScheduler;

    public AcceptCommand(IGameService gameService, IChallengeScheduler challengeScheduler) {
        this.gameService = gameService;
        this.challengeScheduler = challengeScheduler;
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var opponent = Objects.requireNonNull(ctx.getPlayerParam("challenger"));
        var player = ctx.getPlayer();

        if (!challengeScheduler.acceptChallenge(new Challenge(player, opponent))) {
            ctx.reply("No challenge to accept.");
            return;
        }

        try {
            var game = gameService.createGame(player, opponent);
            var image = BoardRenderer.drawBoard(game.board());

            var view = GameStateView.createGameStartView(game, image);
            ctx.replyView(view);
        } catch (AlreadyPlayingException ex) {
            ctx.reply("One or more players are already in a game.");
        }

        LOGGER.info("Player " + player + " accepted challenge from " + opponent);
    }
}
