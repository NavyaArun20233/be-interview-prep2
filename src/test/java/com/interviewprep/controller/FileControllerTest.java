package com.interviewprep.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.interviewprep.dto.PageResponse;
import com.interviewprep.dto.file.FileDownload;
import com.interviewprep.dto.file.FileResponse;
import com.interviewprep.exception.FieldValidationException;
import com.interviewprep.exception.FileTooLargeException;
import com.interviewprep.exception.StoredFileNotFoundException;
import com.interviewprep.exception.UnsupportedFileTypeException;
import com.interviewprep.service.FileService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@WebMvcTest(FileController.class)
class FileControllerTest {

    private static final Instant UPLOADED_AT = Instant.parse("2026-01-15T10:00:00Z");
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final MockMultipartFile PNG_PART =
            new MockMultipartFile("file", "photo.png", MediaType.IMAGE_PNG_VALUE, PNG_BYTES);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    @Test
    void uploadReturns201WithLocationAndMetadata() throws Exception {
        when(fileService.upload(eq("photo.png"), eq(8L), any()))
                .thenReturn(new FileResponse(1, "photo.png", "image/png", 8, UPLOADED_AT));

        mockMvc.perform(multipart("/api/v1/files").file(PNG_PART))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/files/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.originalName").value("photo.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.size").value(8))
                .andExpect(jsonPath("$.uploadedAt").value("2026-01-15T10:00:00Z"));
    }

    @Test
    void uploadOfDisallowedTypeReturns415() throws Exception {
        when(fileService.upload(any(), anyLong(), any()))
                .thenThrow(
                        new UnsupportedFileTypeException("Unsupported file type: only JPEG, PNG and PDF are allowed"));

        mockMvc.perform(multipart("/api/v1/files").file(PNG_PART))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.detail").value("Unsupported file type: only JPEG, PNG and PDF are allowed"))
                .andExpect(jsonPath("$.instance").value("/api/v1/files"));
    }

    @Test
    void uploadOverServiceLimitReturns413() throws Exception {
        when(fileService.upload(any(), anyLong(), any()))
                .thenThrow(new FileTooLargeException("File size 6 bytes exceeds the maximum of 5 bytes"));

        mockMvc.perform(multipart("/api/v1/files").file(PNG_PART))
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("File size 6 bytes exceeds the maximum of 5 bytes"));
    }

    @Test
    void uploadRejectedByMultipartParserReturns413() throws Exception {
        when(fileService.upload(any(), anyLong(), any())).thenThrow(new MaxUploadSizeExceededException(5));

        mockMvc.perform(multipart("/api/v1/files").file(PNG_PART))
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Uploaded file exceeds the maximum allowed size"));
    }

    @Test
    void emptyFileReturns400WithFieldError() throws Exception {
        when(fileService.upload(any(), eq(0L), any()))
                .thenThrow(new FieldValidationException("file", "must not be empty"));

        mockMvc.perform(multipart("/api/v1/files").file(new MockMultipartFile("file", "a.png", null, new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be empty"));
    }

    @Test
    void missingFilePartReturns400WithFieldError() throws Exception {
        mockMvc.perform(multipart("/api/v1/files").file(new MockMultipartFile("other", "a.png", null, PNG_BYTES)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("file"))
                .andExpect(jsonPath("$.errors[0].message").value("is required"));
        verifyNoInteractions(fileService);
    }

    @Test
    void malformedMultipartReturns400() throws Exception {
        when(fileService.upload(any(), anyLong(), any()))
                .thenThrow(new MultipartException("Stream ended unexpectedly"));

        mockMvc.perform(multipart("/api/v1/files").file(PNG_PART))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request is not a valid multipart upload"));
    }

    @Test
    void nonMultipartUploadReturns415() throws Exception {
        mockMvc.perform(post("/api/v1/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(fileService);
    }

    @Test
    void listReturnsPage() throws Exception {
        when(fileService.list(0, 20))
                .thenReturn(new PageResponse<>(
                        List.of(new FileResponse(1, "a.pdf", "application/pdf", 10, UPLOADED_AT)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].originalName").value("a.pdf"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listRejectsPageSizeOverMaximum() throws Exception {
        mockMvc.perform(get("/api/v1/files").param("size", String.valueOf(FileController.MAX_PAGE_SIZE + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        verifyNoInteractions(fileService);
    }

    @Test
    void getReturnsMetadata() throws Exception {
        when(fileService.get(1)).thenReturn(new FileResponse(1, "a.jpg", "image/jpeg", 10, UPLOADED_AT));

        mockMvc.perform(get("/api/v1/files/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalName").value("a.jpg"))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"));
    }

    @Test
    void getUnknownFileReturns404() throws Exception {
        when(fileService.get(42)).thenThrow(new StoredFileNotFoundException(42));

        mockMvc.perform(get("/api/v1/files/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("File 42 not found"));
    }

    @Test
    void downloadReturnsAttachmentWithSafelyEncodedName() throws Exception {
        when(fileService.download(1))
                .thenReturn(new FileDownload(
                        "my \"résumé\".pdf", "application/pdf", new ByteArrayResource(new byte[] {'%', 'P'})));

        mockMvc.perform(get("/api/v1/files/1/content"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(new byte[] {'%', 'P'}))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", containsString("attachment;")))
                .andExpect(header().string(
                                "Content-Disposition",
                                containsString("filename*=UTF-8''my%20%22r%C3%A9sum%C3%A9%22.pdf")));
    }

    @Test
    void downloadOfUnknownFileReturns404() throws Exception {
        when(fileService.download(42)).thenThrow(new StoredFileNotFoundException(42));

        mockMvc.perform(get("/api/v1/files/42/content")).andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/files/1")).andExpect(status().isNoContent());

        verify(fileService).delete(1);
    }

    @Test
    void deleteOfUnknownFileReturns404() throws Exception {
        doThrow(new StoredFileNotFoundException(42)).when(fileService).delete(42);

        mockMvc.perform(delete("/api/v1/files/42")).andExpect(status().isNotFound());
    }
}
