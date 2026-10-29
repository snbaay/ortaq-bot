package kz.homebot.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "shopping_item", indexes = @Index(name = "idx_shopping_household_bought", columnList = "household_id,bought"))
public class ShoppingItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;
    @Column(name = "text", nullable = false, length = 60)
    private String text;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "added_by", nullable = false)
    private Member addedBy;
    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;
    @Column(name = "bought", nullable = false)
    private boolean bought;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bought_by")
    private Member boughtBy;
    @Column(name = "bought_at")
    private LocalDateTime boughtAt;

    public ShoppingItem() {}
    public ShoppingItem(Household household, String text, Member addedBy, LocalDateTime addedAt) {
        this.household = household; this.text = text; this.addedBy = addedBy; this.addedAt = addedAt;
    }
    public Long getId() { return id; }
    public Household getHousehold() { return household; }
    public String getText() { return text; }
    public Member getAddedBy() { return addedBy; }
    public LocalDateTime getAddedAt() { return addedAt; }
    public boolean isBought() { return bought; }
    public Member getBoughtBy() { return boughtBy; }
    public LocalDateTime getBoughtAt() { return boughtAt; }
    public void markBought(Member buyer, LocalDateTime time) {
        bought = true; boughtBy = buyer; boughtAt = time;
    }
}
