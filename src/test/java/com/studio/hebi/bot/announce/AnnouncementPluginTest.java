package com.studio.hebi.bot.announce;

import io.github.yvancywan.anvilcord.core.event.VirtualEventBus;
import io.github.yvancywan.anvilcord.core.plugin.AnvilCordPlugin;
import io.github.yvancywan.anvilcord.core.plugin.AnvilCordPluginContext;
import io.github.yvancywan.anvilcord.discord.command.SlashCommand;
import io.github.yvancywan.anvilcord.discord.command.SlashCommandDefinition;
import io.github.yvancywan.anvilcord.discord.command.SlashCommandInvocationEvent;
import io.github.yvancywan.anvilcord.discord.command.SlashCommandOptionType;
import io.github.yvancywan.anvilcord.discord.event.DiscordBotActions;
import io.github.yvancywan.anvilcord.starter.SlashCommandDefinitionFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnouncementPluginTest {

    @Test
    void isDiscoverableByAnvilCordServiceLoader() {
        List<AnvilCordPlugin> plugins = ServiceLoader.load(AnvilCordPlugin.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        assertTrue(plugins.stream().anyMatch(AnnouncementPlugin.class::isInstance));
    }

    @Test
    void exposesAnnounceCommandContractForDiscordAdapter() {
        assertEquals("announce", AnnouncementPlugin.COMMAND_NAME);
        assertEquals("Publishes an announcement to the selected channel.", AnnouncementPlugin.COMMAND_DESCRIPTION);
        assertEquals("channel", AnnouncementPlugin.TARGET_CHANNEL_OPTION_NAME);
        assertEquals("title", AnnouncementPlugin.TITLE_OPTION_NAME);
        assertEquals("message", AnnouncementPlugin.BODY_OPTION_NAME);
    }

    @Test
    void declaresAnnounceSlashCommandWithAnnotation() {
        SlashCommand command = AnnouncementPlugin.class.getAnnotation(SlashCommand.class);

        assertNotNull(command);
        assertEquals(AnnouncementPlugin.COMMAND_NAME, command.name());
        assertEquals(AnnouncementPlugin.COMMAND_DESCRIPTION, command.description());
        assertEquals(3, command.options().length);
        assertSlashCommandOption(
                command.options()[0],
                AnnouncementPlugin.TARGET_CHANNEL_OPTION_NAME,
                AnnouncementPlugin.TARGET_CHANNEL_OPTION_DESCRIPTION,
                SlashCommandOptionType.CHANNEL
        );
        assertSlashCommandOption(
                command.options()[1],
                AnnouncementPlugin.TITLE_OPTION_NAME,
                AnnouncementPlugin.TITLE_OPTION_DESCRIPTION,
                SlashCommandOptionType.STRING
        );
        assertSlashCommandOption(
                command.options()[2],
                AnnouncementPlugin.BODY_OPTION_NAME,
                AnnouncementPlugin.BODY_OPTION_DESCRIPTION,
                SlashCommandOptionType.STRING
        );
    }

    @Test
    void starterBuildsSlashCommandDefinitionFromAnnotation() {
        SlashCommandDefinition command = SlashCommandDefinitionFactory.fromAnnotation(AnnouncementPlugin.class.getName());

        assertEquals(AnnouncementPlugin.slashCommand(), command);
    }

    @Test
    void doesNotPublishAnnouncementAutoConfigurationMetadata() throws IOException {
        String resourceName = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        try (var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(stream);

            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(content.lines().noneMatch("com.studio.hebi.bot.announce.AnnouncementConfiguration"::equals));
        }
    }

    @Test
    void publishesDiscordBotActionsWhenAnnounceCommandIsInvoked() {
        VirtualEventBus eventBus = new VirtualEventBus();
        AnvilCordPluginContext context = new AnvilCordPluginContext(eventBus);
        List<DiscordBotActions.SendChannelMessage> sentMessages = new ArrayList<>();
        List<DiscordBotActions.RespondToInteraction> responses = new ArrayList<>();
        Instant occurredAt = Instant.parse("2026-05-23T12:00:00Z");

        context.registerListener(DiscordBotActions.SendChannelMessage.class, sentMessages::add);
        context.registerListener(DiscordBotActions.RespondToInteraction.class, responses::add);
        new AnnouncementPlugin().initialize(context);

        context.publish(new SlashCommandInvocationEvent(
                AnnouncementPlugin.COMMAND_NAME,
                "interaction-success-123",
                "source-channel-456",
                "guild-789",
                "user-123",
                Map.of(
                        AnnouncementPlugin.TARGET_CHANNEL_OPTION_NAME, " target-channel-789 ",
                        AnnouncementPlugin.TITLE_OPTION_NAME, " Server News ",
                        AnnouncementPlugin.BODY_OPTION_NAME, " A new event starts tonight. "
                ),
                occurredAt
        ));

        assertEquals(List.of(new DiscordBotActions.SendChannelMessage(
                "target-channel-789",
                "**Server News**\n\nA new event starts tonight.",
                "announce:interaction-success-123:send",
                occurredAt
        )), sentMessages);
        assertEquals(List.of(new DiscordBotActions.RespondToInteraction(
                "interaction-success-123",
                "Announcement submitted.",
                "announce:interaction-success-123:ack",
                occurredAt
        )), responses);
    }

    @Test
    void handlesDuplicateAnnounceInteractionsOnlyOnce() {
        VirtualEventBus eventBus = new VirtualEventBus();
        AnvilCordPluginContext context = new AnvilCordPluginContext(eventBus);
        List<DiscordBotActions.SendChannelMessage> sentMessages = new ArrayList<>();
        List<DiscordBotActions.RespondToInteraction> responses = new ArrayList<>();
        Instant occurredAt = Instant.parse("2026-05-23T12:00:00Z");

        context.registerListener(DiscordBotActions.SendChannelMessage.class, sentMessages::add);
        context.registerListener(DiscordBotActions.RespondToInteraction.class, responses::add);
        new AnnouncementPlugin().initialize(context);
        new AnnouncementPlugin().initialize(context);

        SlashCommandInvocationEvent duplicateEvent = new SlashCommandInvocationEvent(
                AnnouncementPlugin.COMMAND_NAME,
                "interaction-duplicate-123",
                "source-channel-456",
                "guild-789",
                "user-123",
                Map.of(
                        AnnouncementPlugin.TARGET_CHANNEL_OPTION_NAME, "target-channel-789",
                        AnnouncementPlugin.TITLE_OPTION_NAME, "Server News",
                        AnnouncementPlugin.BODY_OPTION_NAME, "A new event starts tonight."
                ),
                occurredAt
        );

        context.publish(duplicateEvent);
        context.publish(duplicateEvent);

        assertEquals(List.of(new DiscordBotActions.SendChannelMessage(
                "target-channel-789",
                "**Server News**\n\nA new event starts tonight.",
                "announce:interaction-duplicate-123:send",
                occurredAt
        )), sentMessages);
        assertEquals(List.of(new DiscordBotActions.RespondToInteraction(
                "interaction-duplicate-123",
                "Announcement submitted.",
                "announce:interaction-duplicate-123:ack",
                occurredAt
        )), responses);
    }

    @Test
    void respondsWithValidationErrorWhenRequiredOptionsAreMissing() {
        VirtualEventBus eventBus = new VirtualEventBus();
        AnvilCordPluginContext context = new AnvilCordPluginContext(eventBus);
        List<DiscordBotActions.SendChannelMessage> sentMessages = new ArrayList<>();
        List<DiscordBotActions.RespondToInteraction> responses = new ArrayList<>();
        Instant occurredAt = Instant.parse("2026-05-23T12:00:00Z");

        context.registerListener(DiscordBotActions.SendChannelMessage.class, sentMessages::add);
        context.registerListener(DiscordBotActions.RespondToInteraction.class, responses::add);
        new AnnouncementPlugin().initialize(context);

        context.publish(new SlashCommandInvocationEvent(
                AnnouncementPlugin.COMMAND_NAME,
                "interaction-validation-123",
                "source-channel-456",
                "guild-789",
                "user-123",
                Map.of(
                        AnnouncementPlugin.TARGET_CHANNEL_OPTION_NAME, "target-channel-789",
                        AnnouncementPlugin.BODY_OPTION_NAME, "A new event starts tonight."
                ),
                occurredAt
        ));

        assertEquals(List.of(), sentMessages);
        assertEquals(List.of(new DiscordBotActions.RespondToInteraction(
                "interaction-validation-123",
                "Please provide a channel, title, and message for the announcement.",
                "announce:interaction-validation-123:validation-error",
                occurredAt
        )), responses);
    }

    @Test
    void ignoresOtherSlashCommands() {
        VirtualEventBus eventBus = new VirtualEventBus();
        AnvilCordPluginContext context = new AnvilCordPluginContext(eventBus);
        List<DiscordBotActions.SendChannelMessage> sentMessages = new ArrayList<>();
        List<DiscordBotActions.RespondToInteraction> responses = new ArrayList<>();
        Instant occurredAt = Instant.parse("2026-05-23T12:00:00Z");

        context.registerListener(DiscordBotActions.SendChannelMessage.class, sentMessages::add);
        context.registerListener(DiscordBotActions.RespondToInteraction.class, responses::add);
        new AnnouncementPlugin().initialize(context);

        context.publish(new SlashCommandInvocationEvent(
                "not-announce",
                "interaction-123",
                "source-channel-456",
                "guild-789",
                "user-123",
                Map.of(),
                occurredAt
        ));

        assertEquals(List.of(), sentMessages);
        assertEquals(List.of(), responses);
    }

    private static void assertSlashCommandOption(
            SlashCommand.Option option,
            String name,
            String description,
            SlashCommandOptionType type
    ) {
        assertEquals(name, option.name());
        assertEquals(description, option.description());
        assertEquals(type, option.type());
        assertTrue(option.required());
    }
}
