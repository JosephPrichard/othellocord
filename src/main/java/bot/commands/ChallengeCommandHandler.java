package bot.commands;

import bot.JDASingleton;
import bot.commands.abstracts.CommandContext;
import bot.commands.abstracts.CommandHandler;
import bot.dtos.ChallengeDto;
import bot.services.ChallengeService;
import bot.dtos.PlayerDto;
import bot.messages.challenge.ChallengeMessageBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.logging.Logger;

public class ChallengeCommandHandler extends CommandHandler
{
    private final Logger logger = Logger.getLogger("command.challenge");
    private final ChallengeService challengeService;

    public ChallengeCommandHandler(ChallengeService challengeService) {
        super(
            "Challenges an another discord player to an Othello game",
            "opponent"
        );
        this.challengeService = challengeService;
    }

    @Override
    public void doCommand(CommandContext ctx) {
        MessageReceivedEvent event = ctx.getEvent();
        MessageChannel channel = event.getChannel();

        User opponentUser = JDASingleton.fetchUserFromDirect(ctx.getParam("opponent"));
        if (opponentUser == null) {
            channel.sendMessage("Can't find a discord user with that id. Try using @ directly.").queue();
            return;
        }

        PlayerDto opponent = new PlayerDto(opponentUser);
        PlayerDto player = new PlayerDto(event.getAuthor());

        long id = player.getId();
        Runnable onExpiry = () -> channel.sendMessage("<@" + id + "> Challenge timed out!").queue();
        challengeService.createChallenge(new ChallengeDto(opponent, player), onExpiry);

        new ChallengeMessageBuilder()
            .setChallenged(opponent)
            .setChallenger(player)
            .sendMessage(channel);

        logger.info("Player " + player + " challenged opponent " + opponent);
    }
}
