package com.studio.hebi.bot.announce;

import io.github.yvancywan.anvilcord.core.plugin.AnvilCordPlugin;
import io.github.yvancywan.anvilcord.core.plugin.AnvilCordPluginContext;
import io.github.yvancywan.anvilcord.discord.command.SimpleSlashCommand;
import io.github.yvancywan.anvilcord.discord.command.SlashCommand;
import io.github.yvancywan.anvilcord.discord.command.SlashCommandDefinition;
import io.github.yvancywan.anvilcord.discord.command.SlashCommandInvocationEvent;
import io.github.yvancywan.anvilcord.discord.command.SlashCommandOptionType;
import io.github.yvancywan.anvilcord.discord.event.DiscordBotActions;
import io.github.yvancywan.anvilcord.discord.event.DiscordGatewayEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SlashCommand(
        name = DiceRollPlugin.COMMAND_NAME,
        description = DiceRollPlugin.COMMAND_DESCRIPTION,
        options = {
                @SlashCommand.Option(
                        name = DiceRollPlugin.DICE_OPTION_NAME,
                        description = DiceRollPlugin.DICE_OPTION_DESCRIPTION,
                        type = SlashCommandOptionType.STRING,
                        required = true
                )
        }
)
public class DiceRollPlugin implements AnvilCordPlugin {

    private static final System.Logger log = System.getLogger(DiceRollPlugin.class.getName());

    public static final String PLUGIN_ID = "dice-roll-plugin";
    public static final String COMMAND_NAME = "roll";
    public static final String COMMAND_DESCRIPTION = "Rolls dice using notation like d20, 2d6, or 4d6+2.";
    public static final String DICE_OPTION_NAME = "dice";
    public static final String DICE_OPTION_DESCRIPTION = "Dice notation to roll, for example d20, 2d6, or 4d6+2.";
    public static final int MAX_DICE_COUNT = 100;
    public static final int MAX_DIE_SIDES = 1_000;
    private static final String VALIDATION_ERROR = "Please provide dice notation like `d20`, `2d6`, or `4d6+2`.";
    private static final Pattern DICE_NOTATION = Pattern.compile(
            "^\\s*(?:(\\d{1,3})\\s*)?[dD]\\s*(\\d{1,5})(?:\\s*([+-])\\s*(\\d{1,6}))?\\s*$"
    );
    private static final int MAX_HANDLED_INTERACTION_IDS = 1_024;
    private static final Set<String> HANDLED_INTERACTION_IDS = Collections.synchronizedSet(
            Collections.newSetFromMap(new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_HANDLED_INTERACTION_IDS;
                }
            })
    );

    private final Random random;

    public DiceRollPlugin() {
        this(new Random());
    }

    DiceRollPlugin(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public String id() {
        return PLUGIN_ID;
    }

    @Override
    public void initialize(AnvilCordPluginContext context) {
        log.log(System.Logger.Level.INFO, "Initializing dice roll plugin listener for /{0}", COMMAND_NAME);
        context.registerListener(DiscordGatewayEvent.class, DiceRollPlugin::logRawSlashCommandEvent);
        context.registerListener(SlashCommandInvocationEvent.class, event -> handleCommand(context, event));
        context.registerListener(DiscordBotActions.ActionFailed.class, DiceRollPlugin::logActionFailed);
        context.registerListener(DiscordBotActions.ActionSucceeded.class, DiceRollPlugin::logActionSucceeded);
    }

    static SlashCommandDefinition slashCommand() {
        return new SimpleSlashCommand(
                COMMAND_NAME,
                COMMAND_DESCRIPTION,
                List.of(new SlashCommandDefinition.Option(
                        DICE_OPTION_NAME,
                        DICE_OPTION_DESCRIPTION,
                        SlashCommandOptionType.STRING,
                        true
                ))
        );
    }

    private void handleCommand(AnvilCordPluginContext context, SlashCommandInvocationEvent event) {
        log.log(System.Logger.Level.DEBUG, "Dice plugin received slash command invocation commandName={0} interactionId={1} channelId={2} guildId={3} userId={4} options={5}",
                event.commandName(), event.interactionId(), event.channelId(), event.guildId(), event.userId(), event.options());
        if (!COMMAND_NAME.equals(event.commandName())) {
            return;
        }

        if (!markInteractionHandled(event.interactionId())) {
            return;
        }

        Optional<DiceExpression> expression = parseDiceExpression(event.options().get(DICE_OPTION_NAME));
        if (expression.isEmpty()) {
            log.log(System.Logger.Level.DEBUG, "Publishing dice validation response for slash interaction {0}", event.interactionId());
            context.publish(new DiscordBotActions.RespondToInteraction(
                    event.interactionId(),
                    VALIDATION_ERROR,
                    correlationId(event, "validation-error"),
                    event.occurredAt()
            ));
            return;
        }

        RollResult result = roll(expression.get());
        log.log(System.Logger.Level.DEBUG, "Publishing dice result response for slash interaction {0}", event.interactionId());
        context.publish(new DiscordBotActions.RespondToInteraction(
                event.interactionId(),
                formatRollResult(result),
                correlationId(event, "result"),
                event.occurredAt()
        ));
    }

    private static void logRawSlashCommandEvent(DiscordGatewayEvent event) {
        Object discordEvent = invoke(event, "discordEvent").orElse(null);
        if (discordEvent != null && "discord4j.core.event.domain.interaction.ChatInputInteractionEvent".equals(discordEvent.getClass().getName())) {
            log.log(System.Logger.Level.DEBUG, "Dice plugin observed raw Discord slash interaction event: {0}", discordEvent.getClass().getName());
        }
    }

    private static void logActionFailed(DiscordBotActions.ActionFailed failure) {
        String correlationId = failure.correlationId();
        if (correlationId != null && correlationId.startsWith(COMMAND_NAME + ":")) {
            log.log(System.Logger.Level.WARNING, "Dice bot action failed actionType={0} correlationId={1} errorMessage={2}",
                    failure.actionType(), failure.correlationId(), failure.errorMessage());
        }
    }

    private static void logActionSucceeded(DiscordBotActions.ActionSucceeded success) {
        String correlationId = success.correlationId();
        if (correlationId != null && correlationId.startsWith(COMMAND_NAME + ":")) {
            log.log(System.Logger.Level.DEBUG, "Dice bot action succeeded actionType={0} correlationId={1} resultId={2}",
                    success.actionType(), success.correlationId(), success.resultId());
        }
    }

    private static Optional<Object> invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return Optional.ofNullable(method.invoke(target));
        } catch (IllegalAccessException | NoSuchMethodException exception) {
            return Optional.empty();
        } catch (InvocationTargetException exception) {
            return Optional.empty();
        }
    }


    private static Optional<DiceExpression> parseDiceExpression(String notation) {
        if (notation == null || notation.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = DICE_NOTATION.matcher(notation);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        int count = matcher.group(1) == null ? 1 : Integer.parseInt(matcher.group(1));
        int sides = Integer.parseInt(matcher.group(2));
        int modifier = parseModifier(matcher.group(3), matcher.group(4));
        if (count < 1 || count > MAX_DICE_COUNT || sides < 2 || sides > MAX_DIE_SIDES) {
            return Optional.empty();
        }

        return Optional.of(new DiceExpression(count, sides, modifier));
    }

    private static int parseModifier(String sign, String value) {
        if (sign == null || value == null) {
            return 0;
        }

        int modifier = Integer.parseInt(value);
        return "-".equals(sign) ? -modifier : modifier;
    }

    private RollResult roll(DiceExpression expression) {
        List<Integer> rolls = new ArrayList<>(expression.count());
        int subtotal = 0;
        for (int rollIndex = 0; rollIndex < expression.count(); rollIndex++) {
            int roll = random.nextInt(expression.sides()) + 1;
            rolls.add(roll);
            subtotal += roll;
        }

        return new RollResult(expression, rolls, subtotal + expression.modifier());
    }

    private static String formatRollResult(RollResult result) {
        return "Rolled " + result.expression().normalizedNotation()
                + ": " + result.total()
                + " (" + String.join(" + ", result.rolls().stream().map(String::valueOf).toList())
                + formatModifier(result.expression().modifier()) + ")";
    }

    private static String formatModifier(int modifier) {
        if (modifier == 0) {
            return "";
        }

        return modifier > 0 ? " + " + modifier : " - " + Math.abs(modifier);
    }

    private static boolean markInteractionHandled(String interactionId) {
        return HANDLED_INTERACTION_IDS.add(interactionId);
    }

    private static String correlationId(SlashCommandInvocationEvent event, String action) {
        return COMMAND_NAME + ":" + event.interactionId() + ":" + action;
    }


    private record DiceExpression(int count, int sides, int modifier) {

        String normalizedNotation() {
            String dice = (count == 1 ? "d" : count + "d") + sides;
            return dice + formatSignedModifier(modifier);
        }

        private static String formatSignedModifier(int modifier) {
            if (modifier == 0) {
                return "";
            }

            return modifier > 0 ? "+" + modifier : String.valueOf(modifier);
        }
    }

    private record RollResult(DiceExpression expression, List<Integer> rolls, int total) {
    }
}
