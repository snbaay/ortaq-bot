package kz.homebot.bot;

import kz.homebot.bot.menu.RepaymentMenu;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "prod"})
public class RepaymentDeliveryJob {
    private final HomeTelegramBot bot;
    private final RepaymentMenu repayments;

    public RepaymentDeliveryJob(HomeTelegramBot bot, RepaymentMenu repayments) {
        this.bot = bot;
        this.repayments = repayments;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void deliver() {
        repayments.retryDue(bot.output());
    }
}
