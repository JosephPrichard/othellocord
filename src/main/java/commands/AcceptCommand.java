/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import lombok.AllArgsConstructor;
import models.Challenge;
import domain.BoardRenderer;
import services.*;

import java.util.Objects;

import static utils.Log.LOGGER;

@AllArgsConstructor
public class AcceptCommand extends CommandHandler {

    private final GameService gameService;
    private final ChallengeScheduler challengeScheduler;

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
        } catch (GameService.AlreadyPlayingException ex) {
            ctx.reply("One or more players are already in a game.");
        }

        LOGGER.info("Player " + player + " accepted challenge from " + opponent);
    }
}
