package com.interviewprep.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;

/**
 * One patient's claim on one doctor's slot. The partial unique index {@code uq_bookings_active_slot} guarantees at
 * most one HELD or CONFIRMED booking per doctor and slot; {@code version} guards state transitions that race with each
 * other (confirm vs. cancel vs. expiry).
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_id", nullable = false, updatable = false)
    private Long doctorId;

    @Column(name = "slot_start", nullable = false, updatable = false)
    private Instant slotStart;

    @Column(name = "patient_name", nullable = false, length = 100, updatable = false)
    private String patientName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingStatus status;

    @Column(name = "hold_expires_at", nullable = false, updatable = false)
    private Instant holdExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    private long version;

    protected Booking() {}

    public static Booking hold(
            long doctorId, Instant slotStart, String patientName, Instant now, Duration holdDuration) {
        Booking booking = new Booking();
        booking.doctorId = doctorId;
        booking.slotStart = slotStart;
        booking.patientName = patientName;
        booking.status = BookingStatus.HELD;
        booking.holdExpiresAt = now.plus(holdDuration);
        booking.createdAt = now;
        booking.updatedAt = now;
        return booking;
    }

    /** A HELD booking whose hold time has run out, whether or not it has been marked EXPIRED yet. */
    public boolean isHoldOverdue(Instant now) {
        return status == BookingStatus.HELD && !holdExpiresAt.isAfter(now);
    }

    public void confirm(Instant now) {
        transition(BookingStatus.CONFIRMED, now);
        confirmedAt = now;
    }

    public void cancel(Instant now) {
        transition(BookingStatus.CANCELLED, now);
    }

    public void expire(Instant now) {
        transition(BookingStatus.EXPIRED, now);
    }

    private void transition(BookingStatus target, Instant now) {
        status = target;
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public Instant getSlotStart() {
        return slotStart;
    }

    public String getPatientName() {
        return patientName;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public long getVersion() {
        return version;
    }
}
