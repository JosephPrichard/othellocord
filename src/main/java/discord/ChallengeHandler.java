/*
 * Copyright (c) Joseph Prichard 2024.
 */

package discord;

import engine.BoardRenderer;
import lombok.AllArgsConstructor;
import models.Challenge;
import models.Game;
import models.Player;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import services.ChallengeScheduler;
import services.GameService;
import utils.EventUtils;

import java.awt.image.BufferedImage;
import java.util.Objects;

import static utils.LogUtils.LOGGER;

@AllArgsConstructor
public class ChallengeHandler {
    private GameService gameService;
    private ChallengeScheduler challengeScheduler;

    public void handleAccept(SlashCommandInteraction event) {
        Player opponent = Objects.requireNonNull(EventUtils.getPlayerParam(event, "challenger"));
        Player player = new Player(event.getUser());

        if (!challengeScheduler.acceptChallenge(new Challenge(player, opponent))) {
            event.reply("No challenge to accept.").queue();
            return;
        }

        try {
            Game game = gameService.createGame(player, opponent);
            BufferedImage image = BoardRenderer.drawBoard(game.getBoard());

            GameView view = GameView.createGameStartView(game, image);
            EventUtils.replyView(event, view);
        } catch (GameService.AlreadyPlayingException ex) {
            event.reply("One or more players are already in a game.").queue();
        }

        LOGGER.info("Player {} accepted challenge from {}", player, opponent);
    }

    public void handleChallenge(SlashCommandInteraction event) {
        switch (Objects.requireNonNull(event.getSubcommandName())) {
            case "bot" -> handleBotChallenge(event);
            case "user" -> handleUserChallenge(event);
            default -> throw new IllegalStateException("Invalid subcommand for the challenge command");
        }
    }

    private void handleBotChallenge(SlashCommandInteraction event) {
        Long level = EventUtils.getLongParam(event, "level");
        if (level == null) {
            level = 3L;
        }

        if (Player.Bot.isInvalidLevel(level)) {
            event.reply("Invalid level. Type !help analyze for valid levels.").queue();
            return;
        }

        Player player = new Player(event.getUser());

        try {
            Game game = gameService.createBotGame(player, level);
            BufferedImage image = BoardRenderer.drawBoardMoves(game.getBoard());

            GameView view = GameView.createGameStartView(game, image);
            EventUtils.replyView(event, view);
        } catch (GameService.AlreadyPlayingException ex) {
            event.reply("You're already in a game").queue();
        }

        LOGGER.info("{} challenged the bot", player);
    }

    private void handleUserChallenge(SlashCommandInteraction event) {
        Player opponent = Objects.requireNonNull(EventUtils.getPlayerParam(event, "opponent"));

        Player player = new Player(event.getUser());

        Runnable onExpiry = () -> event.getChannel().sendMessage(player.toAtString() + " Challenge timed out!").queue();
        challengeScheduler.createChallenge(new Challenge(opponent, player), onExpiry);

        String message = String.format("%s, %s has challenged you to a game of Othello. Type `/accept` %s, or ignore to decline",
            opponent, player.getName(), player.toAtString());
        event.reply(message).queue();

        LOGGER.info("{} challenged opponent {}", player, opponent);
    }
}
