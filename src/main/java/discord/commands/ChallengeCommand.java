/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.commands;

import discord.JDASingleton;
import discord.message.GameStartSender;
import discord.renderers.OthelloBoardRenderer;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import services.Challenge;
import services.ChallengeService;
import services.GameService;
import services.Player;
import discord.message.ChallengeBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import services.exceptions.AlreadyPlayingException;
import utils.Bot;

import java.util.logging.Logger;

import static utils.Bot.MAX_BOT_LEVEL;
import static utils.Logger.LOGGER;

public class ChallengeCommand extends Command
{
    private final ChallengeService challengeService;
    private final GameService gameService;
    private final OthelloBoardRenderer boardRenderer;

    public ChallengeCommand(ChallengeService challengeService, GameService gameService, OthelloBoardRenderer boardRenderer) {
        super("challenge", "Challenges the bot or another user to an Othello game",
            new SubcommandData("user", "Challenges another user to a game")
                .addOption(OptionType.INTEGER, "opponent", "The opponent to challenge", true),
            new SubcommandData("bot", "Challenges the bot to a game")
                .addOption(OptionType.INTEGER, "level", "Level of the bot between 1 and " + MAX_BOT_LEVEL, false));
        this.challengeService = challengeService;
        this.gameService = gameService;
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
            var game = gameService.createBotGame(player, (int) level);
            var image = boardRenderer.drawBoardMoves(game.getBoard());

            var sender = new GameStartSender()
                .setGame(game)
                .setImage(image);
            sender.sendReply(ctx);
        } catch(AlreadyPlayingException ex) {
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
        challengeService.createChallenge(new Challenge(opponent, player), onExpiry);

        var message = new ChallengeBuilder()
            .setChallenged(opponent)
            .setChallenger(player)
            .build();
        ctx.reply(message);

        LOGGER.info("Player " + player + " challenged opponent " + opponent);
    }
}
