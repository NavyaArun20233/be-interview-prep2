package com.interviewprep.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Metadata of an uploaded file whose bytes are stored on disk as {@code storedName}.
 *
 * <p>Rows are immutable once inserted (upload and delete only), so there is no {@code @Version}.
 */
@Entity
@Table(name = "stored_files")
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 64, unique = true)
    private String storedName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected StoredFile() {
        // for JPA
    }

    public StoredFile(String originalName, String storedName, String contentType, long sizeBytes, Instant uploadedAt) {
        this.originalName = originalName;
        this.storedName = storedName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() {
        return id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
