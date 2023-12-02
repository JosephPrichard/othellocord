/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import messaging.builders.ChallengeBuilder;
import messaging.senders.GameStartSender;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import othello.BoardRenderer;
import services.game.Challenge;
import services.game.ChallengeScheduler;
import services.game.GameStorage;
import services.game.Player;
import services.game.exceptions.AlreadyPlayingException;
import utils.Bot;

import static utils.Bot.MAX_BOT_LEVEL;
import static utils.Logger.LOGGER;

public class ChallengeCommand extends Command {
    private final ChallengeScheduler challengeScheduler;
    private final GameStorage gameStorage;
    private final BoardRenderer boardRenderer;

    public ChallengeCommand(ChallengeScheduler challengeScheduler, GameStorage gameStorage, BoardRenderer boardRenderer) {
        super("challenge", "Challenges the bot or another user to an Othello game",
            new SubcommandData("user", "Challenges another user to a game")
                .addOption(OptionType.USER, "opponent", "The opponent to challenge", true),
            new SubcommandData("bot", "Challenges the bot to a game")
                .addOption(OptionType.INTEGER, "level", "Level of the bot between 1 and " + MAX_BOT_LEVEL, false));
        this.challengeScheduler = challengeScheduler;
        this.gameStorage = gameStorage;
        this.boardRenderer = boardRenderer;
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
            var image = boardRenderer.drawBoardMoves(game.board());

            var sender = new GameStartSender()
                .setGame(game)
                .setImage(image);
            sender.sendReply(ctx);
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
        challengeScheduler.createChallenge(new Challenge(opponent, player), onExpiry);

        var message = new ChallengeBuilder()
            .setChallenged(opponent)
            .setChallenger(player)
            .build();
        ctx.reply(message);

        LOGGER.info("Player " + player + " challenged opponent " + opponent);
    }
}
