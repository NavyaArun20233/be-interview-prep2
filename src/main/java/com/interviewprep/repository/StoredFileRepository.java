package com.interviewprep.repository;

import com.interviewprep.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    /** Returns the number of deleted rows, so of two concurrent deletes exactly one sees {@code 1}. */
    @Modifying
    @Query("delete from StoredFile f where f.id = :id")
    int deleteRowById(@Param("id") long id);
}
