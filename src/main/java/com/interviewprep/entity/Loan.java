package com.interviewprep.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** One borrowing of a book by a member; open while {@code returnedAt} is {@code null}. */
@Entity
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false, updatable = false)
    private Book book;

    @Column(name = "member_name", nullable = false, length = 100, updatable = false)
    private String memberName;

    @Column(name = "borrowed_at", nullable = false, updatable = false)
    private Instant borrowedAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    protected Loan() {
        // for JPA
    }

    public Loan(Book book, String memberName, Instant borrowedAt) {
        this.book = book;
        this.memberName = memberName;
        this.borrowedAt = borrowedAt;
    }

    public void markReturned(Instant now) {
        this.returnedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public String getMemberName() {
        return memberName;
    }

    public Instant getBorrowedAt() {
        return borrowedAt;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }
}
