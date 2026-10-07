package com.interviewprep.exception;

/** The hold ran out before it was confirmed; the slot has been released. 410. */
public class HoldExpiredException extends ResourceGoneException {

    public HoldExpiredException(long id) {
        super("The hold on booking " + id + " has expired");
    }
}
