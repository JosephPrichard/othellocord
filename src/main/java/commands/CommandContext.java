/*
 * Copyright (c) Joseph Prichard 2024.
 */

package commands;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import models.Player;

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

    void sendView(String message);

    void replyEmbeds(MessageEmbed embed);

    void replyView(GameView view);

    void replyView(GameView view, Consumer<InteractionHook> onSuccess);

    void sendView(GameView view);
}
