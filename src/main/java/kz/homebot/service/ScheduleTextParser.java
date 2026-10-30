package kz.homebot.service;

import java.time.DayOfWeek;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class ScheduleTextParser {
    private static final Pattern LINE = Pattern.compile("^([^:\\s]+)\\s*:?\\s*(.*?)\\s*$");
    private final WeekdayParser weekdays;

    public ScheduleTextParser(WeekdayParser weekdays) { this.weekdays = weekdays; }

    public Map<DayOfWeek, String> parse(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Empty schedule");
        Map<DayOfWeek, String> assignments = new LinkedHashMap<>();
        for (String raw : input.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            var match = LINE.matcher(line);
            if (!match.matches()) throw new IllegalArgumentException("Invalid schedule line");
            DayOfWeek day = weekdays.parse(match.group(1));
            String cooks = match.group(2).trim().replaceAll("\\s+", " ");
            if (cooks.isEmpty() || assignments.containsKey(day)) {
                throw new IllegalArgumentException("Missing cooks or duplicate weekday");
            }
            assignments.put(day, "-".equals(cooks) ? "" : cooks);
        }
        if (assignments.isEmpty()) throw new IllegalArgumentException("Empty schedule");
        return assignments;
    }
}
