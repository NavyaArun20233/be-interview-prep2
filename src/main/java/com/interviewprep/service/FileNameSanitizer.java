package com.interviewprep.service;

/**
 * Turns a client-supplied file name into a safe display name. The result is only stored in the database and echoed in
 * {@code Content-Disposition}; it is never used to build a filesystem path.
 */
public final class FileNameSanitizer {

    /** Matches the {@code original_name} column length. */
    static final int MAX_LENGTH = 255;

    static final String FALLBACK_NAME = "unnamed";

    private static final int MAX_KEPT_EXTENSION_LENGTH = 16;

    private FileNameSanitizer() {}

    /**
     * Keeps only the last path segment (after the last {@code /} or {@code \}), drops control and invisible formatting
     * characters (e.g. newlines, right-to-left overrides), trims, and limits the length while keeping a short
     * extension. Names that end up empty, {@code .} or {@code ..} become {@value #FALLBACK_NAME}.
     */
    public static String sanitize(String originalName) {
        if (originalName == null) {
            return FALLBACK_NAME;
        }
        int lastSeparator = Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\'));
        StringBuilder cleaned = new StringBuilder();
        originalName
                .substring(lastSeparator + 1)
                .codePoints()
                .filter(cp -> !Character.isISOControl(cp) && Character.getType(cp) != Character.FORMAT)
                .forEach(cleaned::appendCodePoint);
        String name = cleaned.toString().strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return FALLBACK_NAME;
        }
        return name.length() > MAX_LENGTH ? truncate(name) : name;
    }

    private static String truncate(String name) {
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 && name.length() - dot <= MAX_KEPT_EXTENSION_LENGTH ? name.substring(dot) : "";
        int end = MAX_LENGTH - extension.length();
        if (Character.isHighSurrogate(name.charAt(end - 1))) {
            end--; // never split a surrogate pair
        }
        return name.substring(0, end) + extension;
    }
}
