package kz.homebot.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "cooking_duty", uniqueConstraints = @UniqueConstraint(
        name = "uk_cooking_duty_household_date", columnNames = {"household_id", "duty_date"}))
public class CookingDuty {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;
    @Column(name = "duty_date", nullable = false)
    private LocalDate dutyDate;

    public CookingDuty() {}
    public CookingDuty(Household household, Member member, LocalDate dutyDate) {
        this.household = household; this.member = member; this.dutyDate = dutyDate;
    }
    public Long getId() { return id; }
    public Household getHousehold() { return household; }
    public Member getMember() { return member; }
    public void setMember(Member member) { this.member = member; }
    public LocalDate getDutyDate() { return dutyDate; }
}
