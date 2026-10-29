package kz.homebot.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class AllowedChats {
    private final Set<Long> ids;
    public AllowedChats(String config) {
        ids = config == null || config.isBlank() ? Set.of() : Arrays.stream(config.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).collect(Collectors.toUnmodifiableSet());
    }
    public boolean allows(Long chatId) { return ids.isEmpty() || ids.contains(chatId); }
}
