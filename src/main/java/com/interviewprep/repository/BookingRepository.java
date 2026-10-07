package com.interviewprep.repository;

import com.interviewprep.entity.Booking;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Marks the overdue hold (if any) on one doctor's slot EXPIRED, so it leaves the partial unique index and a new hold
     * can be inserted in the same transaction. Bumps {@code version} so a racing confirm/cancel fails.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Booking b
               SET b.status = com.interviewprep.entity.BookingStatus.EXPIRED,
                   b.updatedAt = :now,
                   b.version = b.version + 1
             WHERE b.doctorId = :doctorId
               AND b.slotStart = :slotStart
               AND b.status = com.interviewprep.entity.BookingStatus.HELD
               AND b.holdExpiresAt <= :now
            """)
    int expireOverdueHold(
            @Param("doctorId") long doctorId, @Param("slotStart") Instant slotStart, @Param("now") Instant now);

    /** Housekeeping: marks every overdue hold EXPIRED. */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Booking b
               SET b.status = com.interviewprep.entity.BookingStatus.EXPIRED,
                   b.updatedAt = :now,
                   b.version = b.version + 1
             WHERE b.status = com.interviewprep.entity.BookingStatus.HELD
               AND b.holdExpiresAt <= :now
            """)
    int expireOverdueHolds(@Param("now") Instant now);

    /** Slot starts in {@code [from, to)} taken by a confirmed booking or by a hold that has not run out at {@code now}. */
    @Query("""
            SELECT b.slotStart
              FROM Booking b
             WHERE b.doctorId = :doctorId
               AND b.slotStart >= :from
               AND b.slotStart < :to
               AND (b.status = com.interviewprep.entity.BookingStatus.CONFIRMED
                    OR (b.status = com.interviewprep.entity.BookingStatus.HELD AND b.holdExpiresAt > :now))
            """)
    List<Instant> findTakenSlotStarts(
            @Param("doctorId") long doctorId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("now") Instant now);
}
