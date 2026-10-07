package com.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.interviewprep.exception.UnsupportedFileTypeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileTypeDetectorTest {

    static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46);
    static final byte[] PNG = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
    static final byte[] PDF = "%PDF-1.7".getBytes();
    /** Windows executables start with "MZ". */
    static final byte[] EXE = bytes(0x4D, 0x5A, 0x90, 0x00, 0x03, 0x00, 0x00, 0x00);

    @ParameterizedTest
    @ValueSource(strings = {"photo.jpg", "photo.JPEG", "photo"})
    void acceptsJpegWithMatchingOrNoExtension(String name) {
        assertThat(FileTypeDetector.detect(JPEG, name)).isEqualTo(FileType.JPEG);
    }

    @Test
    void acceptsPngAndPdf() {
        assertThat(FileTypeDetector.detect(PNG, "image.png")).isEqualTo(FileType.PNG);
        assertThat(FileTypeDetector.detect(PDF, "report.pdf")).isEqualTo(FileType.PDF);
        assertThat(FileType.PDF.mediaType()).isEqualTo("application/pdf");
    }

    @Test
    void rejectsExecutableRenamedToPng() {
        assertThatThrownBy(() -> FileTypeDetector.detect(EXE, "photo.png"))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessage("Unsupported file type: only JPEG, PNG and PDF are allowed");
    }

    @Test
    void rejectsExtensionThatDoesNotMatchContent() {
        assertThatThrownBy(() -> FileTypeDetector.detect(PDF, "photo.png"))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessage("File extension '.png' does not match the file content (application/pdf)");
        assertThatThrownBy(() -> FileTypeDetector.detect(PNG, "script.exe"))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void rejectsContentShorterThanAnySignature() {
        assertThatThrownBy(() -> FileTypeDetector.detect(bytes(0xFF, 0xD8), "a.jpg"))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void rejectsPartialPngSignature() {
        byte[] almostPng = PNG.clone();
        almostPng[7] = 0x00;
        assertThatThrownBy(() -> FileTypeDetector.detect(almostPng, "a.png"))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
