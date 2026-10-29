package kz.homebot.bot;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CommandParserTest {

    private final CommandParser parser = new CommandParser();
    private final String botUsername = "OrtaqUiBot";

    @Test
    void parseSimpleCommandWithoutArgs() {
        Optional<CommandParser.ParsedCommand> result = parser.parse("/start", botUsername);
        assertThat(result).isPresent();
        assertThat(result.get().command()).isEqualTo("start");
        assertThat(result.get().args()).isEmpty();
    }

    @Test
    void parseCommandWithBotSuffixAndArgs() {
        Optional<CommandParser.ParsedCommand> result = parser.parse("/shygyn@OrtaqUiBot 630 water", botUsername);
        assertThat(result).isPresent();
        assertThat(result.get().command()).isEqualTo("shygyn");
        assertThat(result.get().args()).isEqualTo("630 water");
    }

    @Test
    void multilineScheduleArgumentsArePreserved() {
        var result = parser.parse("/kezek_qoyu\nдүйсенбі: @one\nсейсенбі: @two", botUsername);
        assertThat(result).isPresent();
        assertThat(result.get().command()).isEqualTo("kezek_qoyu");
        assertThat(result.get().args()).isEqualTo("дүйсенбі: @one\nсейсенбі: @two");
    }

    @Test
    void ignoreCommandAddressedToOtherBot() {
        Optional<CommandParser.ParsedCommand> result = parser.parse("/cmd@OtherBot test", botUsername);
        assertThat(result).isEmpty();
    }

    @Test
    void commandAndBotSuffixCaseInsensitive() {
        Optional<CommandParser.ParsedCommand> result = parser.parse("/SHYGYN@ortaquibot 1000 bread", botUsername);
        assertThat(result).isPresent();
        assertThat(result.get().command()).isEqualTo("shygyn");
        assertThat(result.get().args()).isEqualTo("1000 bread");
    }

    @Test
    void handleExtraSpaces() {
        Optional<CommandParser.ParsedCommand> result = parser.parse("   /balans     ", botUsername);
        assertThat(result).isPresent();
        assertThat(result.get().command()).isEqualTo("balans");
        assertThat(result.get().args()).isEmpty();

        Optional<CommandParser.ParsedCommand> withArgs = parser.parse("   /shygyn    500   milk   ", botUsername);
        assertThat(withArgs).isPresent();
        assertThat(withArgs.get().command()).isEqualTo("shygyn");
        assertThat(withArgs.get().args()).isEqualTo("500   milk");
    }

    @Test
    void plainTextIsNotACommand() {
        assertThat(parser.parse("hello", botUsername)).isEmpty();
        assertThat(parser.parse("630 su", botUsername)).isEmpty();
        assertThat(parser.parse("", botUsername)).isEmpty();
        assertThat(parser.parse(null, botUsername)).isEmpty();
        assertThat(parser.parse("/", botUsername)).isEmpty();
        assertThat(parser.parse("/@OrtaqUiBot", botUsername)).isEmpty();
    }
}
