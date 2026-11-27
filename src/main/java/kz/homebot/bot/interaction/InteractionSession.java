package kz.homebot.bot.interaction;

import java.time.Instant;

public record InteractionSession(String id, long householdId, Long userId, long chatId, String kind,
                                 String state, String payload, Integer promptId, Instant promptExpiresAt,
                                 Integer menuId, int version, Instant expiresAt, Instant closedAt) {
    public boolean activeAt(Instant now) {
        return closedAt == null && expiresAt.isAfter(now);
    }
}
