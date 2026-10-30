package kz.homebot.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "payment_deadline", indexes = @Index(name = "idx_deadline_household_active", columnList = "household_id,active"))
public class PaymentDeadline {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;
    @Column(name = "title", nullable = false, length = 40)
    private String title;
    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;
    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private Member createdBy;
    @Column(name = "active", nullable = false)
    private boolean active = true;

    public PaymentDeadline() {}
    public PaymentDeadline(Household household, String title, int dayOfMonth, BigDecimal amount, Member createdBy) {
        this.household = household; this.title = title; this.dayOfMonth = dayOfMonth;
        this.amount = amount; this.createdBy = createdBy;
    }
    public Long getId() { return id; }
    public Household getHousehold() { return household; }
    public String getTitle() { return title; }
    public int getDayOfMonth() { return dayOfMonth; }
    public BigDecimal getAmount() { return amount; }
    public Member getCreatedBy() { return createdBy; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
