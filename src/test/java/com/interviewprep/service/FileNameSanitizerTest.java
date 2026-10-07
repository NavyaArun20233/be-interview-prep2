package com.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FileNameSanitizerTest {

    @ParameterizedTest
    @CsvSource({
        "../../etc/passwd, passwd",
        "'..\\..\\x.png', x.png",
        "C:\\Users\\me\\photo.jpg, photo.jpg",
        "/abs/path/report.pdf, report.pdf",
        "'  spaced name.png  ', spaced name.png",
        "résumé.pdf, résumé.pdf"
    })
    void keepsOnlyTheLastPathSegment(String input, String expected) {
        assertThat(FileNameSanitizer.sanitize(input)).isEqualTo(expected);
    }

    @Test
    void stripsControlAndFormattingCharacters() {
        assertThat(FileNameSanitizer.sanitize("evil\r\nSet-Cookie: x.png")).isEqualTo("evilSet-Cookie: x.png");
        assertThat(FileNameSanitizer.sanitize("invoice\u202Egnp.exe")).isEqualTo("invoicegnp.exe");
        assertThat(FileNameSanitizer.sanitize("nul\u0000l.png")).isEqualTo("null.png");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"..", ".", "../", "a/..", "   ", "\n"})
    void fallsBackWhenNothingUsableRemains(String input) {
        assertThat(FileNameSanitizer.sanitize(input)).isEqualTo(FileNameSanitizer.FALLBACK_NAME);
    }

    @Test
    void truncatesLongNamesKeepingTheExtension() {
        String sanitized = FileNameSanitizer.sanitize("a".repeat(300) + ".png");

        assertThat(sanitized).hasSize(FileNameSanitizer.MAX_LENGTH).endsWith("a.png");
    }

    @Test
    void doesNotSplitSurrogatePairsWhenTruncating() {
        String emoji = "\uD83D\uDE00"; // one code point, two chars
        // "aa" puts a high surrogate exactly at the cut position (index 250 = 255 - ".pdf".length() - 1)
        String sanitized = FileNameSanitizer.sanitize("aa" + emoji.repeat(200) + ".pdf");

        assertThat(sanitized).hasSize(FileNameSanitizer.MAX_LENGTH - 1).endsWith(emoji + ".pdf");
    }
}
