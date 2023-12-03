/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import messaging.builders.ChallengeBuilder;
import messaging.senders.GameStartSender;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import othello.BoardRenderer;
import services.challenge.Challenge;
import services.challenge.ChallengeManager;
import services.game.GameStorage;
import services.player.Player;
import services.game.exceptions.AlreadyPlayingException;
import utils.Bot;

import static utils.Bot.MAX_BOT_LEVEL;
import static utils.Logger.LOGGER;

public class ChallengeCommand extends Command {
    private final ChallengeManager challengeManager;
    private final GameStorage gameStorage;

    public ChallengeCommand(ChallengeManager challengeManager, GameStorage gameStorage) {
        super("challenge", "Challenges the bot or another user to an Othello game",
            new SubcommandData("user", "Challenges another user to a game")
                .addOption(OptionType.USER, "opponent", "The opponent to challenge", true),
            new SubcommandData("bot", "Challenges the bot to a game")
                .addOption(OptionType.INTEGER, "level", "Level of the bot between 1 and " + MAX_BOT_LEVEL, false));
        this.challengeManager = challengeManager;
        this.gameStorage = gameStorage;
    }

    @Override
    public void doCommand(CommandContext ctx) {
        switch (ctx.subcommand()) {
            case "bot" -> doBotCommand(ctx);
            case "user" -> doUserCommand(ctx);
            default -> throw new IllegalStateException("Invalid subcommand for the challenge command");
        }
    }

    public void doBotCommand(CommandContext ctx) {
        var levelOpt = ctx.getOptionalParam("level");
        var level = levelOpt != null ? levelOpt.getAsLong() : 3;

        if (!Bot.isValidLevel(level)) {
            ctx.reply("Invalid level. Type !help analyze for valid levels.");
            return;
        }

        var player = new Player(ctx.getAuthor());

        try {
            var game = gameStorage.createBotGame(player, (int) level);
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
        var opponentUser = ctx.getParam("opponent").getAsUser();

        var opponent = new Player(opponentUser);
        var player = new Player(ctx.getAuthor());

        var id = player.getId();
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
