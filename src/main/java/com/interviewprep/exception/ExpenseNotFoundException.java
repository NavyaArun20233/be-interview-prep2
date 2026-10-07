package com.interviewprep.exception;

public class ExpenseNotFoundException extends ResourceNotFoundException {

    public ExpenseNotFoundException(long id) {
        super("Expense " + id + " not found");
    }
}
