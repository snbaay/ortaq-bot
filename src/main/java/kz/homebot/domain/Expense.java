package kz.homebot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "expense")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private Member payer;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "settlement", nullable = false)
    private boolean settlement = false;

    public Expense() {
    }

    public Expense(Household household, Member payer, BigDecimal amount, String description) {
        this(household, payer, amount, description, LocalDateTime.now(), false, false);
    }

    public Expense(Household household, Member payer, BigDecimal amount, String description, LocalDateTime createdAt, boolean deleted) {
        this(household, payer, amount, description, createdAt, deleted, false);
    }

    public Expense(Household household, Member payer, BigDecimal amount, String description, LocalDateTime createdAt, boolean deleted, boolean settlement) {
        this.household = household;
        this.payer = payer;
        this.amount = amount;
        this.description = description;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.deleted = deleted;
        this.settlement = settlement;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Household getHousehold() {
        return household;
    }

    public void setHousehold(Household household) {
        this.household = household;
    }

    public Member getPayer() {
        return payer;
    }

    public void setPayer(Member payer) {
        this.payer = payer;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isSettlement() {
        return settlement;
    }

    public void setSettlement(boolean settlement) {
        this.settlement = settlement;
    }
}
