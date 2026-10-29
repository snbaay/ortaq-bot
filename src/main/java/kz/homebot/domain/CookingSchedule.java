package kz.homebot.domain;

import jakarta.persistence.*;
import java.time.DayOfWeek;

@Entity
@Table(name = "cooking_schedule", uniqueConstraints = @UniqueConstraint(
        name = "uk_cooking_schedule_household_day_member", columnNames = {"household_id", "day_of_week", "member_id"}),
        indexes = @Index(name = "idx_cooking_schedule_household_day", columnList = "household_id,day_of_week"))
public class CookingSchedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 9)
    private DayOfWeek dayOfWeek;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    protected CookingSchedule() {}
    public CookingSchedule(Household household, DayOfWeek dayOfWeek, Member member) {
        this.household = household; this.dayOfWeek = dayOfWeek; this.member = member;
    }
    public Long getId() { return id; }
    public Household getHousehold() { return household; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public Member getMember() { return member; }
}
