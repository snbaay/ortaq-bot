package kz.homebot.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chore_log", indexes = @Index(name = "idx_chore_household", columnList = "household_id"))
public class ChoreLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;
    @Column(name = "text", nullable = false, length = 100)
    private String text;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ChoreLog() {}
    public ChoreLog(Household household, Member member, String text, LocalDateTime createdAt) {
        this.household = household; this.member = member; this.text = text; this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public Household getHousehold() { return household; }
    public Member getMember() { return member; }
    public String getText() { return text; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
