/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands.context;

import commands.messaging.MessageSender;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import services.player.Player;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public interface CommandContext {

    String subcommand();

    Player getPlayer();

    User getUser();

    @Nullable
    OptionMapping getParam(String key);

    @Nullable
    Long getLongParam(String key);

    @Nullable
    Player getPlayerParam(String key);

    @Nullable
    String getStringParam(String key);

    void reply(String message);

    void reply(String message, Consumer<InteractionHook> onSuccess);

    void sendMessage(String message);

    void replyEmbeds(MessageEmbed embed);

    void replyWithSender(MessageSender sender);

    void msgWithSender(MessageSender sender);
}
