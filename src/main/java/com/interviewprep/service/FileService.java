package com.interviewprep.service;

import com.interviewprep.config.StorageProperties;
import com.interviewprep.dto.PageResponse;
import com.interviewprep.dto.file.FileDownload;
import com.interviewprep.dto.file.FileResponse;
import com.interviewprep.entity.StoredFile;
import com.interviewprep.exception.FieldValidationException;
import com.interviewprep.exception.FileTooLargeException;
import com.interviewprep.exception.StoredFileNotFoundException;
import com.interviewprep.repository.StoredFileRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Validates, stores, serves and deletes uploaded files. The bytes live on disk ({@link FileStorage}) under a random
 * name; the metadata lives in {@code stored_files}.
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("uploadedAt"), Sort.Order.desc("id"));

    private final StoredFileRepository repository;
    private final FileStorage storage;
    private final StorageProperties properties;
    private final Clock clock;

    public FileService(
            StoredFileRepository repository, FileStorage storage, StorageProperties properties, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Not {@code @Transactional}: the file is written first and the row is inserted by the repository's own
     * transaction, which has committed (or failed) when {@code save} returns. If the insert fails the file is deleted,
     * so a record never points to a missing file and a failed upload leaves no file behind.
     *
     * @param originalName client-supplied name; sanitized and used for display only
     * @param size size in bytes as reported by the multipart parser
     */
    public FileResponse upload(String originalName, long size, InputStreamSource content) {
        if (size <= 0) {
            throw new FieldValidationException("file", "must not be empty");
        }
        long maxBytes = properties.maxFileSize().toBytes();
        if (size > maxBytes) {
            throw new FileTooLargeException(
                    "File size " + size + " bytes exceeds the maximum of " + maxBytes + " bytes");
        }
        String displayName = FileNameSanitizer.sanitize(originalName);
        FileType type = FileTypeDetector.detect(readHeader(content), displayName);

        String storedName = UUID.randomUUID().toString();
        long written = storage.store(storedName, content);
        Instant uploadedAt = clock.instant().truncatedTo(ChronoUnit.MICROS); // PostgreSQL precision
        StoredFile saved;
        try {
            saved = repository.save(new StoredFile(displayName, storedName, type.mediaType(), written, uploadedAt));
        } catch (RuntimeException ex) {
            try {
                storage.delete(storedName);
            } catch (UncheckedIOException cleanupFailure) {
                ex.addSuppressed(cleanupFailure); // report the save failure; the orphan shows up in its stack trace
            }
            throw ex;
        }
        log.info("Stored file {} ({}, {} bytes)", saved.getId(), type.mediaType(), written);
        return FileResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<FileResponse> list(int page, int size) {
        return PageResponse.from(repository.findAll(PageRequest.of(page, size, NEWEST_FIRST)), FileResponse::from);
    }

    @Transactional(readOnly = true)
    public FileResponse get(long id) {
        return FileResponse.from(find(id));
    }

    /**
     * @throws UncheckedIOException (500) if the record exists but its content is missing on disk — an inconsistency
     *     that needs attention, not a client error
     */
    @Transactional(readOnly = true)
    public FileDownload download(long id) {
        StoredFile file = find(id);
        Resource content = storage.load(file.getStoredName());
        return new FileDownload(file.getOriginalName(), file.getContentType(), content);
    }

    /**
     * Deletes the record, then the file once the deletion has committed. The reverse order could leave a record
     * pointing to a missing file if the transaction failed; this order can at worst leave an orphan file on disk,
     * which no API can reach and which is logged as a WARN for cleanup.
     */
    @Transactional
    public void delete(long id) {
        StoredFile file = find(id);
        if (repository.deleteRowById(id) == 0) {
            throw new StoredFileNotFoundException(id); // deleted concurrently
        }
        String storedName = file.getStoredName();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteContent(id, storedName);
            }
        });
        log.info("Deleted file {}", id);
    }

    private void deleteContent(long id, String storedName) {
        try {
            if (!storage.delete(storedName)) {
                log.warn("Content of deleted file {} ({}) was already missing from storage", id, storedName);
            }
        } catch (UncheckedIOException ex) {
            log.warn("Could not delete content of deleted file {} ({}); orphan left on disk", id, storedName, ex);
        }
    }

    private StoredFile find(long id) {
        return repository.findById(id).orElseThrow(() -> new StoredFileNotFoundException(id));
    }

    private static byte[] readHeader(InputStreamSource content) {
        try (InputStream in = content.getInputStream()) {
            return in.readNBytes(FileType.MAX_SIGNATURE_LENGTH);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read uploaded file", ex);
        }
    }
}
