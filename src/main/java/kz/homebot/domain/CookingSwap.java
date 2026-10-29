package kz.homebot.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "cooking_swap", uniqueConstraints = @UniqueConstraint(
        name = "uk_cooking_swap_household_date_from", columnNames = {"household_id", "swap_date", "from_member_id"}),
        indexes = @Index(name = "idx_cooking_swap_household_date", columnList = "household_id,swap_date"))
public class CookingSwap {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;
    @Column(name = "swap_date", nullable = false)
    private LocalDate date;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_member_id", nullable = false)
    private Member fromMember;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_member_id", nullable = false)
    private Member toMember;

    protected CookingSwap() {}
    public CookingSwap(Household household, LocalDate date, Member fromMember, Member toMember) {
        this.household = household; this.date = date; this.fromMember = fromMember; this.toMember = toMember;
    }
    public Long getId() { return id; }
    public Household getHousehold() { return household; }
    public LocalDate getDate() { return date; }
    public Member getFromMember() { return fromMember; }
    public Member getToMember() { return toMember; }
}
