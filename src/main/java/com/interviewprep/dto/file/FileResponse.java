package com.interviewprep.dto.file;

import com.interviewprep.entity.StoredFile;
import java.time.Instant;

/** Metadata of an uploaded file; {@code size} is in bytes. */
public record FileResponse(long id, String originalName, String contentType, long size, Instant uploadedAt) {

    public static FileResponse from(StoredFile file) {
        return new FileResponse(
                file.getId(), file.getOriginalName(), file.getContentType(), file.getSizeBytes(), file.getUploadedAt());
    }
}
