package com.interviewprep.entity;

/** Lifecycle of a booking. Only HELD and CONFIRMED occupy the slot. */
public enum BookingStatus {
    HELD,
    CONFIRMED,
    CANCELLED,
    EXPIRED
}
