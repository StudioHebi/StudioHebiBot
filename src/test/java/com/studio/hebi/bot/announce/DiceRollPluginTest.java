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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiceRollPluginTest {

    @Test
    void isDiscoverableByAnvilCordServiceLoader() {
        List<AnvilCordPlugin> plugins = ServiceLoader.load(AnvilCordPlugin.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        assertTrue(plugins.stream().anyMatch(DiceRollPlugin.class::isInstance));
    }

    @Test
    void exposesRollCommandContractForDiscordAdapter() {
        assertEquals("roll", DiceRollPlugin.COMMAND_NAME);
        assertEquals("Rolls dice using notation like d20, 2d6, or 4d6+2.", DiceRollPlugin.COMMAND_DESCRIPTION);
        assertEquals("dice", DiceRollPlugin.DICE_OPTION_NAME);
    }

    @Test
    void declaresRollSlashCommandWithAnnotation() {
        SlashCommand command = DiceRollPlugin.class.getAnnotation(SlashCommand.class);

        assertNotNull(command);
        assertEquals(DiceRollPlugin.COMMAND_NAME, command.name());
        assertEquals(DiceRollPlugin.COMMAND_DESCRIPTION, command.description());
        assertEquals(1, command.options().length);
        assertSlashCommandOption(
                command.options()[0],
                DiceRollPlugin.DICE_OPTION_NAME,
                DiceRollPlugin.DICE_OPTION_DESCRIPTION,
                SlashCommandOptionType.STRING
        );
    }

    @Test
    void starterBuildsSlashCommandDefinitionFromAnnotation() {
        SlashCommandDefinition command = SlashCommandDefinitionFactory.fromAnnotation(DiceRollPlugin.class.getName());

        assertEquals(DiceRollPlugin.slashCommand(), command);
    }

    @Test
    void respondsWithRollResultWhenRollCommandIsInvoked() {
        VirtualEventBus eventBus = new VirtualEventBus();
        AnvilCordPluginContext context = new AnvilCordPluginContext(eventBus);
        List<DiscordBotActions.RespondToInteraction> responses = new ArrayList<>();
        Instant occurredAt = Instant.parse("2026-05-23T12:00:00Z");

        context.registerListener(DiscordBotActions.RespondToInteraction.class, responses::add);
        new DiceRollPlugin(new FixedRandom(2, 4)).initialize(context);

        context.publish(new SlashCommandInvocationEvent(
                DiceRollPlugin.COMMAND_NAME,
                "dice-interaction-success-123",
                "source-channel-456",
                "guild-789",
                "user-123",
                Map.of(DiceRollPlugin.DICE_OPTION_NAME, " 2D6 + 4 "),
                occurredAt
        ));

        assertEquals(List.of(new DiscordBotActions.RespondToInteraction(
                "dice-interaction-success-123",
                "Rolled 2d6+4: 12 (3 + 5 + 4)",
                "roll:dice-interaction-success-123:result",
                occurredAt
        )), responses);
    }

    @Test
    void respondsWithValidationErrorWhenDiceNotationIsMissing() {
        VirtualEventBus eventBus = new VirtualEventBus();
        AnvilCordPluginContext context = new AnvilCordPluginContext(eventBus);
        List<DiscordBotActions.RespondToInteraction> responses = new ArrayList<>();
        Instant occurredAt = Instant.parse("2026-05-23T12:00:00Z");

        context.registerListener(DiscordBotActions.RespondToInteraction.class, responses::add);
        new DiceRollPlugin(new FixedRandom()).initialize(context);

        context.publish(new SlashCommandInvocationEvent(
                DiceRollPlugin.COMMAND_NAME,
                "dice-interaction-validation-123",
                "source-channel-456",
                "guild-789",
                "user-123",
                Map.of(),
                occurredAt
        ));

        assertEquals(List.of(new DiscordBotActions.RespondToInteraction(
                "dice-interaction-validation-123",
                "Please provide dice notation like `d20`, `2d6`, or `4d6+2`.",
                "roll:dice-interaction-validation-123:validation-error",
                occurredAt
        )), responses);
    }

    @Test
    void handlesDuplicateRollInteractionsOnlyOnce() {
        VirtualEventBus eventBus = new VirtualEventBus();
        AnvilCordPluginContext context = new AnvilCordPluginContext(eventBus);
        List<DiscordBotActions.RespondToInteraction> responses = new ArrayList<>();
        Instant occurredAt = Instant.parse("2026-05-23T12:00:00Z");

        context.registerListener(DiscordBotActions.RespondToInteraction.class, responses::add);
        new DiceRollPlugin(new FixedRandom(19)).initialize(context);
        new DiceRollPlugin(new FixedRandom(19)).initialize(context);

        SlashCommandInvocationEvent duplicateEvent = new SlashCommandInvocationEvent(
                DiceRollPlugin.COMMAND_NAME,
                "dice-interaction-duplicate-123",
                "source-channel-456",
                "guild-789",
                "user-123",
                Map.of(DiceRollPlugin.DICE_OPTION_NAME, "d20"),
                occurredAt
        );

        context.publish(duplicateEvent);
        context.publish(duplicateEvent);

        assertEquals(List.of(new DiscordBotActions.RespondToInteraction(
                "dice-interaction-duplicate-123",
                "Rolled d20: 20 (20)",
                "roll:dice-interaction-duplicate-123:result",
                occurredAt
        )), responses);
    }

    @Test
    void ignoresOtherSlashCommands() {
        VirtualEventBus eventBus = new VirtualEventBus();
        AnvilCordPluginContext context = new AnvilCordPluginContext(eventBus);
        List<DiscordBotActions.RespondToInteraction> responses = new ArrayList<>();
        Instant occurredAt = Instant.parse("2026-05-23T12:00:00Z");

        context.registerListener(DiscordBotActions.RespondToInteraction.class, responses::add);
        new DiceRollPlugin(new FixedRandom()).initialize(context);

        context.publish(new SlashCommandInvocationEvent(
                "not-roll",
                "dice-interaction-ignored-123",
                "source-channel-456",
                "guild-789",
                "user-123",
                Map.of(DiceRollPlugin.DICE_OPTION_NAME, "d20"),
                occurredAt
        ));

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


    private static final class FixedRandom extends Random {

        private final int[] values;
        private int index;

        private FixedRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            assertTrue(index < values.length, "FixedRandom did not have another value");
            int value = values[index++];
            assertTrue(value >= 0 && value < bound, "FixedRandom value must be within the requested bound");
            return value;
        }
    }
}

