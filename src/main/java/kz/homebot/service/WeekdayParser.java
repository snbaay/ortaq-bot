package kz.homebot.service;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class WeekdayParser {
    private final Map<String, DayOfWeek> aliases = new HashMap<>();

    public WeekdayParser(Map<DayOfWeek, String> names) {
        for (DayOfWeek day : DayOfWeek.values()) {
            String value = names.get(day);
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing weekday aliases");
            aliases.put(Integer.toString(day.getValue()), day);
            for (String alias : value.split(",")) {
                String key = alias.trim().toLowerCase(Locale.ROOT);
                if (key.isEmpty() || aliases.putIfAbsent(key, day) != null) {
                    throw new IllegalArgumentException("Invalid weekday alias");
                }
            }
        }
    }

    public DayOfWeek parse(String input) {
        DayOfWeek day = aliases.get(input == null ? "" : input.trim().toLowerCase(Locale.ROOT));
        if (day == null) throw new IllegalArgumentException("Unknown weekday");
        return day;
    }
}
