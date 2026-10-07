package com.interviewprep.controller;

import com.interviewprep.dto.PageResponse;
import com.interviewprep.dto.file.FileDownload;
import com.interviewprep.dto.file.FileResponse;
import com.interviewprep.service.FileService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    static final int MAX_PAGE_SIZE = 100;

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /** Multipart upload of one JPEG, PNG or PDF in the part {@code file}. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileResponse> upload(@RequestParam("file") MultipartFile file) {
        FileResponse created = fileService.upload(file.getOriginalFilename(), file.getSize(), file);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Newest first. */
    @GetMapping
    public PageResponse<FileResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return fileService.list(page, size);
    }

    @GetMapping("/{id}")
    public FileResponse get(@PathVariable long id) {
        return fileService.get(id);
    }

    /**
     * Always an attachment with the stored (detected) type. {@link ContentDisposition} quotes/encodes the name, so
     * quotes or line breaks in it cannot inject headers; {@code nosniff} stops browsers from re-interpreting the bytes.
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> download(@PathVariable long id) {
        FileDownload file = fileService.download(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.originalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        fileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
