package com.interviewprep.service;

import com.interviewprep.config.StorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Reads and writes file content under the configured storage root. Callers pass server-generated names only; every
 * name is still resolved and checked to stay inside the root (defense in depth against path traversal).
 */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(StorageProperties properties) {
        try {
            Path location = Path.of(properties.location()).toAbsolutePath().normalize();
            this.root = Files.createDirectories(location).toRealPath();
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create storage directory " + properties.location(), ex);
        }
    }

    /** Writes {@code content} to a new file (never overwrites) and returns the number of bytes written. */
    public long store(String storedName, InputStreamSource content) {
        Path target = resolve(storedName);
        try (InputStream in = content.getInputStream()) {
            return Files.copy(in, target);
        } catch (FileAlreadyExistsException ex) {
            throw new UncheckedIOException("Stored file name already taken: " + storedName, ex); // keep existing file
        } catch (IOException ex) {
            deletePartialFile(target);
            throw new UncheckedIOException("Cannot store file " + storedName, ex);
        }
    }

    /** @throws UncheckedIOException wrapping {@link NoSuchFileException} if the file is missing */
    public Resource load(String storedName) {
        Path file = resolve(storedName);
        if (!Files.isRegularFile(file)) {
            throw new UncheckedIOException(new NoSuchFileException(storedName, null, "stored file is missing"));
        }
        return new PathResource(file);
    }

    /** Returns {@code true} if the file existed and was deleted. */
    public boolean delete(String storedName) {
        try {
            return Files.deleteIfExists(resolve(storedName));
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot delete file " + storedName, ex);
        }
    }

    Path resolve(String storedName) {
        Path target = root.resolve(storedName).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IllegalArgumentException("Stored file name resolves outside the storage root");
        }
        return target;
    }

    private static void deletePartialFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException cleanupFailure) {
            // Best effort: the original failure is rethrown by the caller and is the one worth reporting.
        }
    }
}
