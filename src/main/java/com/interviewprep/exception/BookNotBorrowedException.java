package com.interviewprep.exception;

public class BookNotBorrowedException extends ResourceConflictException {

    public BookNotBorrowedException(long id) {
        super("Book " + id + " is not currently borrowed, so it cannot be returned");
    }
}
