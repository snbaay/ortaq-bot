package kz.homebot.config;

import kz.homebot.bot.CommandParser;
import kz.homebot.service.DebtCalculator;
import kz.homebot.service.ExpenseParser;
import kz.homebot.service.ExpenseSplitter;
import kz.homebot.service.MoneyFormatter;
import kz.homebot.service.WeekdayParser;
import kz.homebot.service.ScheduleTextParser;
import kz.homebot.service.EffectiveCooks;
import kz.homebot.service.MessageService;
import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import kz.homebot.service.DigestPolicy;
import kz.homebot.service.ChoreAge;
import kz.homebot.service.PaymentCountdown;
import kz.homebot.service.RandomPicker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LogicConfig {

    @Bean
    public CommandParser commandParser() {
        return new CommandParser();
    }

    @Bean
    public ExpenseSplitter expenseSplitter() {
        return new ExpenseSplitter();
    }

    @Bean
    public ExpenseParser expenseParser() {
        return new ExpenseParser();
    }

    @Bean
    public DebtCalculator debtCalculator() {
        return new DebtCalculator();
    }

    @Bean
    public MoneyFormatter moneyFormatter() {
        return new MoneyFormatter();
    }

    @Bean
    public WeekdayParser weekdayParser(MessageService messages) {
        Map<DayOfWeek, String> aliases = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            aliases.put(day, messages.getMessage("weekday.alias." + day.name().toLowerCase(Locale.ROOT)));
        }
        return new WeekdayParser(aliases);
    }

    @Bean
    public ScheduleTextParser scheduleTextParser(WeekdayParser weekdays) {
        return new ScheduleTextParser(weekdays);
    }

    @Bean
    public EffectiveCooks effectiveCooks() { return new EffectiveCooks(); }

    @Bean
    public DigestPolicy digestPolicy() {
        return new DigestPolicy();
    }

    @Bean
    public ChoreAge choreAge() { return new ChoreAge(); }

    @Bean
    public PaymentCountdown paymentCountdown() { return new PaymentCountdown(); }

    @Bean
    public RandomPicker randomPicker() { return new RandomPicker(new java.util.Random()); }
}
