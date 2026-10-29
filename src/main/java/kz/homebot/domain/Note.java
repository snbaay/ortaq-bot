package kz.homebot.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "note", indexes = @Index(name = "idx_note_household", columnList = "household_id"))
public class Note {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Member author;
    @Column(name = "text", nullable = false, length = 300)
    private String text;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    public Note() {}
    public Note(Household household, Member author, String text, LocalDateTime createdAt) {
        this.household = household; this.author = author; this.text = text; this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public Household getHousehold() { return household; }
    public Member getAuthor() { return author; }
    public String getText() { return text; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
