/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashMap;
import java.util.Map;

public class CommandContext
{
    private MessageReceivedEvent event;
    private String key;
    private final Map<String, String> params = new HashMap<>();

    public MessageReceivedEvent getEvent() {
        return event;
    }

    public void setEvent(MessageReceivedEvent event) {
        this.event = event;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getParam(String key) {
        return params.get(key);
    }

    public void addParam(String key, String value) {
        params.put(key, value);
    }
}
