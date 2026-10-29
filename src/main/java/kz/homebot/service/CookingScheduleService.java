package kz.homebot.service;

import kz.homebot.domain.CookingSchedule;
import kz.homebot.domain.CookingSwap;
import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.CookingScheduleRepository;
import kz.homebot.repository.CookingSwapRepository;
import kz.homebot.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CookingScheduleService {
    public enum Issue { INVALID, UNKNOWN_MEMBER, AMBIGUOUS_MEMBER, NOT_COOKING, SAME_MEMBER }
    public static class ScheduleException extends RuntimeException {
        private final Issue issue;
        public ScheduleException(Issue issue) { this.issue = issue; }
        public Issue issue() { return issue; }
    }

    private final CookingScheduleRepository schedules;
    private final CookingSwapRepository swaps;
    private final MemberRepository members;
    private final ScheduleTextParser textParser;
    private final EffectiveCooks effective;

    public CookingScheduleService(CookingScheduleRepository schedules, CookingSwapRepository swaps,
                                  MemberRepository members, ScheduleTextParser textParser, EffectiveCooks effective) {
        this.schedules = schedules; this.swaps = swaps; this.members = members;
        this.textParser = textParser; this.effective = effective;
    }

    @Transactional
    public void setSchedule(Household household, String text) {
        Map<DayOfWeek, String> parsed;
        try { parsed = textParser.parse(text); }
        catch (IllegalArgumentException e) { throw new ScheduleException(Issue.INVALID); }
        List<Member> householdMembers = members.findByHousehold(household);
        Map<DayOfWeek, List<Member>> resolved = new LinkedHashMap<>();
        for (var entry : parsed.entrySet()) {
            resolved.put(entry.getKey(), entry.getValue().isEmpty()
                    ? List.of() : resolvePeople(householdMembers, entry.getValue()));
        }
        for (DayOfWeek day : resolved.keySet()) schedules.deleteByHouseholdAndDayOfWeek(household, day);
        schedules.flush();
        for (var entry : resolved.entrySet()) {
            for (Member member : entry.getValue()) {
                schedules.save(new CookingSchedule(household, entry.getKey(), member));
            }
        }
    }

    @Transactional
    public void clear(Household household) { schedules.deleteByHousehold(household); }

    @Transactional(readOnly = true)
    public List<Member> effectiveCooks(Household household, LocalDate date) {
        List<Member> all = members.findByHousehold(household);
        Map<Long, Member> byId = all.stream().collect(Collectors.toMap(Member::getId, m -> m));
        List<Long> scheduled = schedules.findByHouseholdAndDayOfWeek(household, date.getDayOfWeek()).stream()
                .map(s -> s.getMember().getId()).sorted().toList();
        List<EffectiveCooks.Swap> todaySwaps = swaps.findByHouseholdAndDateOrderByIdAsc(household, date).stream()
                .map(s -> new EffectiveCooks.Swap(s.getFromMember().getId(), s.getToMember().getId())).toList();
        return effective.apply(scheduled, todaySwaps).stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public Map<DayOfWeek, List<Member>> week(Household household, LocalDate today) {
        Map<DayOfWeek, List<Member>> result = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) result.put(day, new ArrayList<>());
        for (CookingSchedule row : schedules.findByHousehold(household)) {
            result.get(row.getDayOfWeek()).add(row.getMember());
        }
        for (var cooks : result.values()) cooks.sort(Comparator.comparing(Member::getId));
        result.put(today.getDayOfWeek(), effectiveCooks(household, today));
        return result;
    }

    @Transactional
    public Member handOver(Household household, LocalDate date, Member sender, String recipientText) {
        if (sender == null || !household.getId().equals(sender.getHousehold().getId())) {
            throw new ScheduleException(Issue.NOT_COOKING);
        }
        if (effectiveCooks(household, date).stream().noneMatch(m -> m.getId().equals(sender.getId()))) {
            throw new ScheduleException(Issue.NOT_COOKING);
        }
        List<Member> recipients = resolvePeople(members.findByHousehold(household), recipientText);
        if (recipients.size() != 1) throw new ScheduleException(Issue.INVALID);
        Member target = recipients.get(0);
        if (target.getId().equals(sender.getId())) throw new ScheduleException(Issue.SAME_MEMBER);
        swaps.save(new CookingSwap(household, date, sender, target));
        return target;
    }

    private List<Member> resolvePeople(List<Member> all, String input) {
        if (input == null || input.isBlank()) throw new ScheduleException(Issue.INVALID);
        String[] words = input.trim().split("\\s+");
        Map<Long, Member> selected = new LinkedHashMap<>();
        for (int i = 0; i < words.length;) {
            Member chosen = null;
            int consumed = 1;
            if (words[i].startsWith("@")) {
                String username = words[i].substring(1);
                chosen = all.stream().filter(m -> m.getTelegramUsername() != null
                        && m.getTelegramUsername().equalsIgnoreCase(username)).findFirst().orElse(null);
                if (chosen == null) throw new ScheduleException(Issue.UNKNOWN_MEMBER);
            } else {
                for (int end = words.length; end > i; end--) {
                    String prefix = String.join(" ", java.util.Arrays.copyOfRange(words, i, end))
                            .toLowerCase(Locale.ROOT);
                    List<Member> matches = all.stream().filter(m ->
                            m.getDisplayName().toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
                    if (matches.size() > 1) throw new ScheduleException(Issue.AMBIGUOUS_MEMBER);
                    if (matches.size() == 1) { chosen = matches.get(0); consumed = end - i; break; }
                }
                if (chosen == null) throw new ScheduleException(Issue.UNKNOWN_MEMBER);
            }
            selected.put(chosen.getId(), chosen);
            i += consumed;
        }
        return List.copyOf(selected.values());
    }
}
