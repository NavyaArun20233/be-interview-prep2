package com.interviewprep.exception;

public class StoredFileNotFoundException extends ResourceNotFoundException {

    public StoredFileNotFoundException(long id) {
        super("File " + id + " not found");
    }
}
