package kz.homebot.config;

import kz.homebot.bot.CommandParser;
import kz.homebot.service.DebtCalculator;
import kz.homebot.service.ExpenseParser;
import kz.homebot.service.ExpenseSplitter;
import kz.homebot.service.MoneyFormatter;
import kz.homebot.service.NextCookPicker;
import kz.homebot.service.DigestPolicy;
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
    public NextCookPicker nextCookPicker() {
        return new NextCookPicker();
    }

    @Bean
    public DigestPolicy digestPolicy() {
        return new DigestPolicy();
    }
}
