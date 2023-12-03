/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands.context;

import messaging.senders.MessageSender;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import services.player.Player;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public interface CommandContext {

    String subcommand();

    Player getPlayer();

    User getUser();

    OptionMapping getParam(String key);

    @Nullable()
    OptionMapping getOptionalParam(String key);

    void reply(String message);

    void sendMessage(String message, Consumer<Message> onSuccess);

    void sendMessage(String message);

    void deferReply();

    void replyEmbeds(MessageEmbed embed);

    void replyWithSender(MessageSender sender);

    void msgWithSender(MessageSender sender);
}
