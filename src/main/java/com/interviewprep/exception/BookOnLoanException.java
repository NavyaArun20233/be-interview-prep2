package com.interviewprep.exception;

public class BookOnLoanException extends ResourceConflictException {

    public BookOnLoanException(long id) {
        super("Book " + id + " is currently borrowed and cannot be deleted until it is returned");
    }
}
