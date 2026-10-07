package com.interviewprep.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewprep.TestcontainersConfiguration;
import com.interviewprep.dto.file.FileResponse;
import com.interviewprep.repository.StoredFileRepository;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** Upload, list, download and delete through HTTP against PostgreSQL, with storage in a temporary directory. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class FileApiIT {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] EXE_SIGNATURE = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};
    private static final int FIVE_MB = 5 * 1024 * 1024;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void storageLocation(DynamicPropertyRegistry registry) {
        registry.add("app.storage.location", () -> storageRoot().toString());
    }

    /** A sub-directory, so a traversal to {@code ../evil.png} would still land inside {@link #tempDir}. */
    private static Path storageRoot() {
        return tempDir.resolve("storage");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private StoredFileRepository repository;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void uploadListDownloadAndDelete() throws Exception {
        byte[] png = Arrays.copyOf(PNG_SIGNATURE, 1024);
        png[1023] = 42;

        FileResponse created = client.post()
                .uri("/api/v1/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart("holiday photo.png", png))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .valueMatches(HttpHeaders.LOCATION, ".*/api/v1/files/\\d+")
                .expectBody(FileResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(created).isNotNull();
        assertThat(created.originalName()).isEqualTo("holiday photo.png");
        assertThat(created.contentType()).isEqualTo("image/png");
        assertThat(created.size()).isEqualTo(1024);
        Path stored = storageRoot()
                .resolve(repository.findById(created.id()).orElseThrow().getStoredName());
        assertThat(stored).exists();

        client.get()
                .uri("/api/v1/files?size=100")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content[?(@.id == %d)].originalName".formatted(created.id()))
                .isEqualTo(List.of("holiday photo.png"));

        byte[] downloaded = client.get()
                .uri("/api/v1/files/{id}/content", created.id())
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.IMAGE_PNG)
                .expectHeader()
                .valueMatches(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"holiday photo.png\".*")
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();
        assertThat(downloaded).isEqualTo(png);

        client.delete()
                .uri("/api/v1/files/{id}", created.id())
                .exchange()
                .expectStatus()
                .isNoContent();

        assertThat(repository.findById(created.id())).isEmpty();
        assertThat(stored).doesNotExist();
        client.get()
                .uri("/api/v1/files/{id}", created.id())
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void fileOverFiveMegabytesIsRejectedWith413() {
        byte[] tooLarge = Arrays.copyOf(PNG_SIGNATURE, FIVE_MB + 1);
        long before = repository.count();

        client.post()
                .uri("/api/v1/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart("big.png", tooLarge))
                .exchange()
                .expectStatus()
                .isEqualTo(413)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(413);

        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void fileOfExactlyFiveMegabytesIsAccepted() {
        client.post()
                .uri("/api/v1/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart("max.png", Arrays.copyOf(PNG_SIGNATURE, FIVE_MB)))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.size")
                .isEqualTo(FIVE_MB);
    }

    @Test
    void executableRenamedToPngIsRejectedWith415() {
        long before = repository.count();

        client.post()
                .uri("/api/v1/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart("photo.png", Arrays.copyOf(EXE_SIGNATURE, 512)))
                .exchange()
                .expectStatus()
                .isEqualTo(415)
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("Unsupported file type: only JPEG, PNG and PDF are allowed");

        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void traversalInFileNameCannotEscapeStorageRoot() {
        FileResponse created = client.post()
                .uri("/api/v1/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart("../evil.png", Arrays.copyOf(PNG_SIGNATURE, 64)))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(FileResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.originalName()).isEqualTo("evil.png");
        String storedName = repository.findById(created.id()).orElseThrow().getStoredName();
        assertThat(storageRoot().resolve(storedName)).exists();
        assertThat(Files.exists(tempDir.resolve("evil.png"))).isFalse();
        assertThat(Files.exists(storageRoot().resolve("evil.png"))).isFalse();
    }

    private static MultiValueMap<String, Object> multipart(String fileName, byte[] content) {
        // An InputStreamResource has no known length, so the part carries no Content-Length header, like a browser or
        // curl upload. Tomcat then rejects an oversized part after reading the limit (and drains the small rest)
        // instead of rejecting on the declared length and resetting the connection with megabytes still unread.
        InputStreamResource resource = new InputStreamResource(new ByteArrayInputStream(content)) {
            @Override
            public String getFilename() {
                return fileName;
            }

            @Override
            public long contentLength() {
                return -1; // unknown: don't read the one-shot stream to measure it
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.IMAGE_PNG);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, partHeaders));
        return body;
    }
}
