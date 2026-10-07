package com.interviewprep.exception;

public class BookNotFoundException extends ResourceNotFoundException {

    public BookNotFoundException(long id) {
        super("Book " + id + " not found");
    }
}
