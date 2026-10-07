package com.interviewprep.exception;

public class BookAlreadyBorrowedException extends ResourceConflictException {

    public BookAlreadyBorrowedException(long id) {
        super("Book " + id + " is already borrowed and cannot be borrowed again until it is returned");
    }
}
