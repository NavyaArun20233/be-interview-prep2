package com.interviewprep.controller;

import com.interviewprep.dto.PageResponse;
import com.interviewprep.dto.library.BookRequest;
import com.interviewprep.dto.library.BookResponse;
import com.interviewprep.dto.library.BorrowRequest;
import com.interviewprep.dto.library.LoanResponse;
import com.interviewprep.service.BookService;
import com.interviewprep.service.LoanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/books")
public class BookController {

    static final int MAX_PAGE_SIZE = 100;

    private final BookService bookService;
    private final LoanService loanService;

    public BookController(BookService bookService, LoanService loanService) {
        this.bookService = bookService;
        this.loanService = loanService;
    }

    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
        BookResponse created = bookService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** {@code q} filters by title or author (case-insensitive substring); blank or absent lists every book. */
    @GetMapping
    public PageResponse<BookResponse> list(
            @RequestParam(required = false) @Size(max = 255) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return bookService.list(Optional.ofNullable(q), page, size);
    }

    @GetMapping("/{id}")
    public BookResponse get(@PathVariable long id) {
        return bookService.get(id);
    }

    @PutMapping("/{id}")
    public BookResponse update(@PathVariable long id, @Valid @RequestBody BookRequest request) {
        return bookService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        bookService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** 200 rather than 201: loans are not addressable resources, so there is no Location to return. */
    @PostMapping("/{id}/borrow")
    public LoanResponse borrow(@PathVariable long id, @Valid @RequestBody BorrowRequest request) {
        return loanService.borrow(id, request);
    }

    @PostMapping("/{id}/return")
    public LoanResponse returnBook(@PathVariable long id) {
        return loanService.returnBook(id);
    }
}
