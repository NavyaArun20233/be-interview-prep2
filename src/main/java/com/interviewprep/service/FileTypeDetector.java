package com.interviewprep.service;

import com.interviewprep.exception.UnsupportedFileTypeException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Decides a file's type from its content, never from the client's {@code Content-Type} or the name alone: a file
 * renamed to {@code photo.png} is still rejected unless its bytes are a PNG.
 */
public final class FileTypeDetector {

    private FileTypeDetector() {}

    /**
     * @param header the first {@link FileType#MAX_SIGNATURE_LENGTH} bytes of the file (fewer if the file is shorter)
     * @param fileName sanitized original name; if it has an extension, it must belong to the detected type
     * @throws UnsupportedFileTypeException if the content is not an allowed type or the extension does not match it
     */
    public static FileType detect(byte[] header, String fileName) {
        FileType type = Arrays.stream(FileType.values())
                .filter(candidate -> candidate.matches(header))
                .findFirst()
                .orElseThrow(() ->
                        new UnsupportedFileTypeException("Unsupported file type: only JPEG, PNG and PDF are allowed"));
        extension(fileName)
                .filter(extension -> !type.extensions().contains(extension))
                .ifPresent(extension -> {
                    throw new UnsupportedFileTypeException("File extension '." + extension
                            + "' does not match the file content (" + type.mediaType() + ")");
                });
        return type;
    }

    /** Lower-case text after the last dot; empty if the name has no extension. */
    static Optional<String> extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
