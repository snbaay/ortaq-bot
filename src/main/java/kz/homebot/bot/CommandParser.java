package kz.homebot.bot;

import java.util.Optional;

/**
 * Pure class for parsing Telegram bot commands.
 * Handles formats:
 * - /cmd
 * - /cmd@BotUsername
 * - /cmd args
 * - /cmd@BotUsername args
 * Ignores commands addressed to other bots (/cmd@OtherBot).
 * Case-insensitive for command name and bot username suffix.
 */
public class CommandParser {

    public record ParsedCommand(String command, String args) {}

    public Optional<ParsedCommand> parse(String text, String botUsername) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        String trimmed = text.trim();
        if (!trimmed.startsWith("/")) {
            return Optional.empty();
        }

        // Split into the command token (first word) and the remainder (arguments)
        int spaceIndex = trimmed.indexOf(' ');
        String commandToken;
        String args;

        if (spaceIndex < 0) {
            commandToken = trimmed.substring(1);
            args = "";
        } else {
            commandToken = trimmed.substring(1, spaceIndex);
            args = trimmed.substring(spaceIndex + 1).trim();
        }

        if (commandToken.isEmpty()) {
            return Optional.empty();
        }

        String command;
        int atIndex = commandToken.indexOf('@');
        if (atIndex >= 0) {
            command = commandToken.substring(0, atIndex).toLowerCase();
            String targetBot = commandToken.substring(atIndex + 1);
            if (command.isEmpty()) {
                return Optional.empty();
            }
            if (botUsername != null && !botUsername.isBlank() && !targetBot.equalsIgnoreCase(botUsername)) {
                // Addressed to another bot
                return Optional.empty();
            }
        } else {
            command = commandToken.toLowerCase();
        }

        return Optional.of(new ParsedCommand(command, args));
    }
}
