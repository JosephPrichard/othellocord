package discord.commands;

import discord.JDASingleton;
import discord.commands.abstracts.CommandContext;
import discord.commands.abstracts.Command;
import modules.challenge.Challenge;
import modules.game.exceptions.AlreadyPlayingException;
import modules.challenge.ChallengeService;
import modules.game.GameService;
import modules.game.Game;
import modules.player.Player;
import discord.message.senders.GameStartMessageSender;
import discord.renderers.OthelloBoardRenderer;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.image.BufferedImage;
import java.util.logging.Logger;

public class AcceptCommand extends Command
{
    private final Logger logger = Logger.getLogger("command.accept");
    private final GameService gameService;
    private final ChallengeService challengeService;
    private final OthelloBoardRenderer boardRenderer;

    public AcceptCommand(
        GameService gameService,
        ChallengeService challengeService,
        OthelloBoardRenderer boardRenderer
    ) {
        super("accept", "Accepts a challenge from another discord user", "challenger");
        this.gameService = gameService;
        this.challengeService = challengeService;
        this.boardRenderer = boardRenderer;
    }

    @Override
    public void doCommand(CommandContext ctx) {
        MessageReceivedEvent event = ctx.getEvent();
        MessageChannel channel = event.getChannel();

        User opponentUser = JDASingleton.fetchUserFromDirect(ctx.getParam("challenger"));
        if (opponentUser == null) {
            channel.sendMessage("Can't find a discord user with that id. Try using @ directly.").queue();
            return;
        }

        Player opponent = new Player(opponentUser);
        Player player = new Player(event.getAuthor());

        if (!challengeService.acceptChallenge(new Challenge(player, opponent))) {
            channel.sendMessage("No challenge to accept.").queue();
            return;
        }

        try {
            Game game = gameService.createGame(player, opponent);
            BufferedImage image = boardRenderer.drawBoard(game.getBoard());

            new GameStartMessageSender()
                .setGame(game)
                .setTag(game)
                .setImage(image)
                .sendMessage(channel);
        } catch(AlreadyPlayingException ex) {
            channel.sendMessage("One ore more players are already in a game.").queue();
        }
        logger.info("Player " + player + " accepted challenge from " + opponent);
    }
}
