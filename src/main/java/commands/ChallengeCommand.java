/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import messaging.builders.ChallengeBuilder;
import messaging.senders.GameStartSender;
import othello.BoardRenderer;
import services.challenge.Challenge;
import services.challenge.ChallengeManager;
import services.game.GameStorage;
import services.game.exceptions.AlreadyPlayingException;
import services.player.Player;

import static utils.Logger.LOGGER;

public class ChallengeCommand extends Command {

    private final ChallengeManager challengeManager;
    private final GameStorage gameStorage;

    public ChallengeCommand(ChallengeManager challengeManager, GameStorage gameStorage) {
        super("challenge");
        this.challengeManager = challengeManager;
        this.gameStorage = gameStorage;
    }

    @Override
    public void onCommand(CommandContext ctx) {
        switch (ctx.subcommand()) {
            case "bot" -> doBotCommand(ctx);
            case "user" -> doUserCommand(ctx);
            default -> throw new IllegalStateException("Invalid subcommand for the challenge command");
        }
    }

    public void doBotCommand(CommandContext ctx) {
        var level = ctx.getLongParam("level");
        if (level == null) {
            level = 3L;
        }

        if (!Player.Bot.isValidLevel(level)) {
            ctx.reply("Invalid level. Type !help analyze for valid levels.");
            return;
        }

        var player = ctx.getPlayer();

        try {
            var game = gameStorage.createBotGame(player, level);
            var image = BoardRenderer.drawBoardMoves(game.board());

            var sender = new GameStartSender()
                .setGame(game)
                .setImage(image);
            ctx.replyWithSender(sender);
        } catch (AlreadyPlayingException ex) {
            ctx.reply("You're already in a game");
        }

        LOGGER.info("Player " + player + " challenged the bot");
    }

    public void doUserCommand(CommandContext ctx) {
        var opponent = ctx.getPlayerParam("opponent");

        var player = ctx.getPlayer();

        var id = player.id();
        Runnable onExpiry = () -> ctx.sendMessage("<@" + id + "> Challenge timed out!");
        challengeManager.createChallenge(new Challenge(opponent, player), onExpiry);

        var message = new ChallengeBuilder()
            .setChallenged(opponent)
            .setChallenger(player)
            .build();
        ctx.reply(message);

        LOGGER.info("Player " + player + " challenged opponent " + opponent);
    }
}
