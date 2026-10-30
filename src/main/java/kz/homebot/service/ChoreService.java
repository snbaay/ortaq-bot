package kz.homebot.service;

import kz.homebot.domain.ChoreLog;
import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.ChoreLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import kz.homebot.config.Limits;

@Service
public class ChoreService {
    private final ChoreLogRepository logs;
    public ChoreService(ChoreLogRepository logs) { this.logs = logs; }

    @Transactional
    public ChoreLog add(Household household, Member member, String text) {
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.isEmpty() || cleaned.length() > Limits.CHORE) throw new IllegalArgumentException("Invalid chore");
        return logs.save(new ChoreLog(household, member, cleaned, LocalDateTime.now(ZoneId.of("Asia/Almaty"))));
    }
    @Transactional(readOnly = true)
    public List<ChoreLog> latest(Household household) { return logs.findTop10ByHouseholdOrderByIdDesc(household); }
    @Transactional(readOnly = true)
    public Optional<ChoreLog> findLast(Household household, String text) {
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.isEmpty()) return Optional.empty();
        return logs.findFirstByHouseholdAndTextContainingIgnoreCaseOrderByIdDesc(household, cleaned);
    }
}
