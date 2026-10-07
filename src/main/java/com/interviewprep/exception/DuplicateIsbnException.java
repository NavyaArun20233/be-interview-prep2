package com.interviewprep.exception;

public class DuplicateIsbnException extends ResourceConflictException {

    public DuplicateIsbnException(String isbn) {
        super("A book with ISBN " + isbn + " already exists");
    }
}
