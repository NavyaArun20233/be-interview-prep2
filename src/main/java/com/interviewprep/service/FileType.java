package com.interviewprep.service;

import java.util.Arrays;
import java.util.Set;

/** The file types the upload API accepts, each identified by the magic bytes its content starts with. */
public enum FileType {
    JPEG("image/jpeg", Set.of("jpg", "jpeg"), 0xFF, 0xD8, 0xFF),
    PNG("image/png", Set.of("png"), 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
    PDF("application/pdf", Set.of("pdf"), 0x25, 0x50, 0x44, 0x46, 0x2D);

    /** Bytes that must be read from the start of a file to recognize every type. */
    public static final int MAX_SIGNATURE_LENGTH = 8;

    private final String mediaType;
    private final Set<String> extensions;
    private final byte[] signature;

    FileType(String mediaType, Set<String> extensions, int... signature) {
        this.mediaType = mediaType;
        this.extensions = extensions;
        this.signature = new byte[signature.length];
        for (int i = 0; i < signature.length; i++) {
            this.signature[i] = (byte) signature[i];
        }
    }

    public String mediaType() {
        return mediaType;
    }

    /** Lower-case extensions (without the dot) a file of this type may have. */
    public Set<String> extensions() {
        return extensions;
    }

    boolean matches(byte[] header) {
        return header.length >= signature.length
                && Arrays.equals(header, 0, signature.length, signature, 0, signature.length);
    }
}
