/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands.messaging;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import othello.Tile;
import services.game.Game;
import services.player.Player;
import utils.ImageUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

// allows for building complex messages that send embeds, images, and messages as replies or directly to channels
public class MessageSender {

    protected final EmbedBuilder embed;
    private BufferedImage image;
    protected String message;

    public static MessageSender createGameStartSender(Game game, BufferedImage image) {
        var desc = "Black: " + game.blackPlayer().name() + "\n " +
            "White: " + game.whitePlayer().name() + "\n " +
            "Use `/view` to view the game and use `/move` to make a move.";
        var embed = new EmbedBuilder()
            .setTitle("Game started!")
            .setDescription(desc);

        return new MessageSender(embed).setImage(image);
    }

    public static String getScoreText(Game game) {
        return "Black: " + game.getBlackScore() + " points \n" +
            "White: " + game.getWhiteScore() + " points \n";
    }

    public static MessageSender createGameViewSender(Game game, Tile move, BufferedImage image) {
        var desc = getScoreText(game) + "Your opponent has moved: " + move;

        var embed = new EmbedBuilder()
            .setTitle("Your game with " + game.getOtherPlayer().name())
            .setDescription(desc)
            .setFooter(game.isBlackMove() ? "Black to move" : "White to move");;

        return new MessageSender(embed)
            .setTag(game.getCurrentPlayer())
            .setImage(image);
    }

    public static MessageSender createGameViewSender(Game game, BufferedImage image) {
        var desc = getScoreText(game) + game.getCurrentPlayer().name() + " to move";

        var embed = new EmbedBuilder()
            .setTitle(game.blackPlayer().name() + " vs " + game.whitePlayer().name())
            .setDescription(desc)
            .setFooter(game.isBlackMove() ? "Black to move" : "White to move");

        return new MessageSender(embed)
            .setTag(game.getCurrentPlayer())
            .setImage(image);
    }

    public static MessageSender createGameAnalyzeSender(Game game, BufferedImage image, long level) {
        var desc = getScoreText(game);

        var embed = new EmbedBuilder()
        .setTitle("Game Analysis using bot level " + level)
        .setDescription(desc)
        .setFooter("Positive heuristics are better for black, and negative heuristics are better for white");

        return new MessageSender(embed)
            .setTag(game.getCurrentPlayer())
            .setImage(image);
    }

    public MessageSender(EmbedBuilder embed) {
        this.embed = embed;
        this.embed.setColor(Color.GREEN);
    }

    public MessageSender setImage(BufferedImage image) {
        this.image = image;
        return this;
    }

    public MessageSender setTag(Player player) {
        if (!player.isBot()) {
            message = "<@" + player + ">";
        }
        return this;
    }

    public void sendReply(SlashCommandInteraction event) {
        try {
            var is = ImageUtils.toPngInputStream(image);
            embed.setImage("attachment://image.png");

            if (message != null) {
                event.getChannel()
                    .sendMessage(message)
                    .queue();
            }
            event.replyEmbeds(embed.build())
                .addFile(is, "image.png")
                .queue();
        } catch (IOException ex) {
            event.reply("Unexpected error: couldn't create image").queue();
        }
    }

    public void sendMessage(SlashCommandInteraction event) {
        try {
            var is = ImageUtils.toPngInputStream(image);
            embed.setImage("attachment://image.png");

            if (message != null) {
                event.getChannel()
                    .sendMessage(message)
                    .queue();
            }
            event.getChannel().sendMessageEmbeds(embed.build())
                .addFile(is, "image.png")
                .queue();
        } catch (IOException ex) {
            event.reply("Unexpected error: couldn't create image").queue();
        }
    }

    public void editMessageUsingHook(InteractionHook hook) {
        try {
            var is = ImageUtils.toPngInputStream(image);
            embed.setImage("attachment://image.png");

            hook.editOriginalEmbeds(embed.build())
                .addFile(is, "image.png")
                .queue();
            if (message != null) {
                hook.editOriginal(message).queue();
            }
        } catch (IOException ex) {
            hook.editOriginal("Unexpected error: couldn't create image").queue();
        }
    }
}
