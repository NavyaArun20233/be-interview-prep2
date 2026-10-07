package com.interviewprep.exception;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Proves the generic exception bases map to Problem Details without feature-specific handler code. */
@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void invalidBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/test/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/test/items"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
    }

    @Test
    void malformedJsonReturns400WithoutErrors() throws Exception {
        mockMvc.perform(post("/test/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request body is missing or is not valid JSON"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void typeMismatchReturns400WithFieldError() throws Exception {
        mockMvc.perform(get("/test/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("must be a valid long"));
    }

    @Test
    void notFoundSubclassReturns404() throws Exception {
        mockMvc.perform(get("/test/items/404"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Item 404 not found"))
                .andExpect(jsonPath("$.instance").value("/test/items/404"));
    }

    @Test
    void businessRuleSubclassReturns422() throws Exception {
        mockMvc.perform(get("/test/items/422"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Item 422 is locked"));
    }

    @Test
    void conflictSubclassReturns409() throws Exception {
        mockMvc.perform(get("/test/items/409")).andExpect(status().isConflict());
    }

    @Test
    void goneSubclassReturns410() throws Exception {
        mockMvc.perform(get("/test/items/410")).andExpect(status().isGone());
    }

    @Test
    void fieldValidationExceptionReturns400WithFieldError() throws Exception {
        mockMvc.perform(get("/test/items/400"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("is reserved"));
    }

    @Test
    void unexpectedExceptionReturns500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/test/items/500"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void unknownPathReturns404ProblemDetail() throws Exception {
        mockMvc.perform(get("/test/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        mockMvc.perform(post("/test/items/1")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unsupportedMediaTypeReturns415() throws Exception {
        mockMvc.perform(post("/test/items").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    record ItemRequest(@NotBlank String name) {}

    static class ItemNotFoundException extends ResourceNotFoundException {
        ItemNotFoundException(long id) {
            super("Item " + id + " not found");
        }
    }

    static class ItemLockedException extends BusinessRuleViolationException {
        ItemLockedException(long id) {
            super("Item " + id + " is locked");
        }
    }

    static class ItemExistsException extends ResourceConflictException {
        ItemExistsException(long id) {
            super("Item " + id + " already exists");
        }
    }

    static class ItemExpiredException extends ResourceGoneException {
        ItemExpiredException(long id) {
            super("Item " + id + " has expired");
        }
    }

    @RestController
    static class TestController {

        @PostMapping("/test/items")
        ItemRequest create(@Valid @RequestBody ItemRequest request) {
            return request;
        }

        @GetMapping("/test/items/{id}")
        String get(@PathVariable long id) {
            if (id == 404) {
                throw new ItemNotFoundException(id);
            }
            if (id == 400) {
                throw new FieldValidationException("id", "is reserved");
            }
            if (id == 409) {
                throw new ItemExistsException(id);
            }
            if (id == 410) {
                throw new ItemExpiredException(id);
            }
            if (id == 422) {
                throw new ItemLockedException(id);
            }
            if (id == 500) {
                throw new IllegalStateException("SELECT * FROM secret_table");
            }
            return "ok";
        }
    }
}
