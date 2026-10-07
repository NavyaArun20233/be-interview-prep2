package com.interviewprep.dto.library;

import com.interviewprep.entity.Book;
import java.time.Instant;

/** A book in the catalog; {@code available} is {@code false} while the book is on loan. */
public record BookResponse(
        Long id,
        String title,
        String author,
        String isbn,
        Integer publishedYear,
        boolean available,
        Instant createdAt,
        Instant updatedAt) {

    public static BookResponse from(Book book, boolean available) {
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn(),
                book.getPublishedYear(),
                available,
                book.getCreatedAt(),
                book.getUpdatedAt());
    }
}
