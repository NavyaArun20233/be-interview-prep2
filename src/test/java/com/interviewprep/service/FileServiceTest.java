package com.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewprep.config.StorageProperties;
import com.interviewprep.dto.file.FileResponse;
import com.interviewprep.entity.StoredFile;
import com.interviewprep.exception.FieldValidationException;
import com.interviewprep.exception.FileTooLargeException;
import com.interviewprep.exception.StoredFileNotFoundException;
import com.interviewprep.exception.UnsupportedFileTypeException;
import com.interviewprep.repository.StoredFileRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;

class FileServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00.123456789Z");

    @TempDir
    Path root;

    private final StoredFileRepository repository = mock(StoredFileRepository.class);
    private FileService service;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(root.toString(), DataSize.ofBytes(100));
        service =
                new FileService(repository, new FileStorage(properties), properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadStoresContentUnderRandomNameAndSavesSanitizedMetadata() throws IOException {
        when(repository.save(any(StoredFile.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 7L));
        byte[] png = png(20);

        FileResponse response = service.upload("../../photo.png", png.length, new ByteArrayResource(png));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.originalName()).isEqualTo("photo.png");
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.uploadedAt()).isEqualTo(Instant.parse("2026-01-15T10:00:00.123456Z"));
        StoredFile saved = savedFile();
        assertThat(saved.getStoredName()).matches("[0-9a-f-]{36}");
        assertThat(Files.readAllBytes(root.resolve(saved.getStoredName()))).isEqualTo(png);
    }

    @Test
    void uploadRejectsEmptyFile() {
        assertThatThrownBy(() -> service.upload("a.png", 0, new ByteArrayResource(new byte[0])))
                .isInstanceOf(FieldValidationException.class)
                .hasMessage("must not be empty");
        assertThat(filesOnDisk()).isEmpty();
    }

    @Test
    void uploadRejectsFileOverLimit() {
        byte[] png = png(101);

        assertThatThrownBy(() -> service.upload("a.png", png.length, new ByteArrayResource(png)))
                .isInstanceOf(FileTooLargeException.class)
                .hasMessage("File size 101 bytes exceeds the maximum of 100 bytes");
        assertThat(filesOnDisk()).isEmpty();
    }

    @Test
    void uploadRejectsRenamedExecutableWithoutWritingIt() {
        byte[] exe = Arrays.copyOf(FileTypeDetectorTest.EXE, 50);

        assertThatThrownBy(() -> service.upload("photo.png", exe.length, new ByteArrayResource(exe)))
                .isInstanceOf(UnsupportedFileTypeException.class);
        assertThat(filesOnDisk()).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void uploadDeletesStoredFileWhenSavingTheRecordFails() {
        when(repository.save(any(StoredFile.class))).thenThrow(new DataIntegrityViolationException("boom"));
        byte[] png = png(20);

        assertThatThrownBy(() -> service.upload("a.png", png.length, new ByteArrayResource(png)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(filesOnDisk()).isEmpty();
    }

    @Test
    void deleteRemovesTheFileOnlyAfterCommit() throws IOException {
        StoredFile file = withId(new StoredFile("a.png", "stored-a", "image/png", 3, NOW), 5L);
        Files.write(root.resolve("stored-a"), new byte[] {1, 2, 3});
        when(repository.findById(5L)).thenReturn(Optional.of(file));
        when(repository.deleteRowById(5L)).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        service.delete(5L);

        assertThat(root.resolve("stored-a")).exists();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(root.resolve("stored-a")).doesNotExist();
    }

    @Test
    void deleteOfUnknownFileThrowsNotFound() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(StoredFileNotFoundException.class);
    }

    private StoredFile savedFile() {
        ArgumentCaptor<StoredFile> captor = ArgumentCaptor.forClass(StoredFile.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private List<Path> filesOnDisk() {
        try (Stream<Path> files = Files.list(root)) {
            return files.toList();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static StoredFile withId(StoredFile file, long id) {
        ReflectionTestUtils.setField(file, "id", id);
        return file;
    }

    static byte[] png(int size) {
        return Arrays.copyOf(FileTypeDetectorTest.PNG, size);
    }
}
