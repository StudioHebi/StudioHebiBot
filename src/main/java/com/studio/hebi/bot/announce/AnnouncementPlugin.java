package com.studio.hebi.bot.announce;

import io.github.yvancywan.anvilcord.core.plugin.AnvilCordPlugin;
import io.github.yvancywan.anvilcord.core.plugin.AnvilCordPluginContext;
import io.github.yvancywan.anvilcord.discord.command.SimpleSlashCommand;
import io.github.yvancywan.anvilcord.discord.command.SlashCommand;
import io.github.yvancywan.anvilcord.discord.command.SlashCommandDefinition;
import io.github.yvancywan.anvilcord.discord.command.SlashCommandInvocationEvent;
import io.github.yvancywan.anvilcord.discord.command.SlashCommandOptionType;
import io.github.yvancywan.anvilcord.discord.event.DiscordBotActions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@SlashCommand(
        name = AnnouncementPlugin.COMMAND_NAME,
        description = AnnouncementPlugin.COMMAND_DESCRIPTION,
        options = {
                @SlashCommand.Option(
                        name = AnnouncementPlugin.TARGET_CHANNEL_OPTION_NAME,
                        description = AnnouncementPlugin.TARGET_CHANNEL_OPTION_DESCRIPTION,
                        type = SlashCommandOptionType.CHANNEL,
                        required = true
                ),
                @SlashCommand.Option(
                        name = AnnouncementPlugin.TITLE_OPTION_NAME,
                        description = AnnouncementPlugin.TITLE_OPTION_DESCRIPTION,
                        type = SlashCommandOptionType.STRING,
                        required = true
                ),
                @SlashCommand.Option(
                        name = AnnouncementPlugin.BODY_OPTION_NAME,
                        description = AnnouncementPlugin.BODY_OPTION_DESCRIPTION,
                        type = SlashCommandOptionType.STRING,
                        required = true
                )
        }
)
public class AnnouncementPlugin implements AnvilCordPlugin {

    public static final String PLUGIN_ID = "announcement-plugin";
    public static final String COMMAND_NAME = "announce";
    public static final String COMMAND_DESCRIPTION = "Publishes an announcement to the selected channel.";
    public static final String TARGET_CHANNEL_OPTION_NAME = "channel";
    public static final String TARGET_CHANNEL_OPTION_DESCRIPTION = "Channel to receive the announcement.";
    public static final String TITLE_OPTION_NAME = "title";
    public static final String TITLE_OPTION_DESCRIPTION = "Announcement title.";
    public static final String BODY_OPTION_NAME = "message";
    public static final String BODY_OPTION_DESCRIPTION = "Announcement message.";
    private static final int MAX_HANDLED_INTERACTION_IDS = 1_024;
    private static final Set<String> HANDLED_INTERACTION_IDS = Collections.synchronizedSet(
            Collections.newSetFromMap(new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_HANDLED_INTERACTION_IDS;
                }
            })
    );

    @Override
    public String id() {
        return PLUGIN_ID;
    }

    @Override
    public void initialize(AnvilCordPluginContext context) {
        context.registerListener(SlashCommandInvocationEvent.class, event -> handleCommand(context, event));
    }

    static SlashCommandDefinition slashCommand() {
        return new SimpleSlashCommand(
                COMMAND_NAME,
                COMMAND_DESCRIPTION,
                List.of(
                        new SlashCommandDefinition.Option(
                                TARGET_CHANNEL_OPTION_NAME,
                                TARGET_CHANNEL_OPTION_DESCRIPTION,
                                SlashCommandOptionType.CHANNEL,
                                true
                        ),
                        new SlashCommandDefinition.Option(
                                TITLE_OPTION_NAME,
                                TITLE_OPTION_DESCRIPTION,
                                SlashCommandOptionType.STRING,
                                true
                        ),
                        new SlashCommandDefinition.Option(
                                BODY_OPTION_NAME,
                                BODY_OPTION_DESCRIPTION,
                                SlashCommandOptionType.STRING,
                                true
                        )
                )
        );
    }

    private static void handleCommand(AnvilCordPluginContext context, SlashCommandInvocationEvent event) {
        if (!COMMAND_NAME.equals(event.commandName())) {
            return;
        }

        if (!markInteractionHandled(event.interactionId())) {
            return;
        }

        Map<String, String> options = event.options();
        Optional<String> targetChannelId = requiredOption(options, TARGET_CHANNEL_OPTION_NAME);
        Optional<String> title = requiredOption(options, TITLE_OPTION_NAME);
        Optional<String> body = requiredOption(options, BODY_OPTION_NAME);

        if (targetChannelId.isEmpty() || title.isEmpty() || body.isEmpty()) {
            context.publish(new DiscordBotActions.RespondToInteraction(
                    event.interactionId(),
                    "Please provide a channel, title, and message for the announcement.",
                    correlationId(event, "validation-error"),
                    event.occurredAt()
            ));
            return;
        }

        context.publish(new DiscordBotActions.SendChannelMessage(
                targetChannelId.get(),
                formatAnnouncement(title.get(), body.get()),
                correlationId(event, "send"),
                event.occurredAt()
        ));
        context.publish(new DiscordBotActions.RespondToInteraction(
                event.interactionId(),
                "Announcement submitted.",
                correlationId(event, "ack"),
                event.occurredAt()
        ));
    }

    private static Optional<String> requiredOption(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(value.trim());
    }

    private static String formatAnnouncement(String title, String body) {
        return "**" + title + "**\n\n" + body;
    }

    private static boolean markInteractionHandled(String interactionId) {
        return HANDLED_INTERACTION_IDS.add(interactionId);
    }

    private static String correlationId(SlashCommandInvocationEvent event, String action) {
        return COMMAND_NAME + ":" + event.interactionId() + ":" + action;
    }
}
