package com.multidime.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Unit tests for ApiExceptionHandler. The handler methods are called directly.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    private static String errorOf(ResponseEntity<Map<String, String>> response) {
        return response.getBody().get("error");
    }

    @Test
    @DisplayName("A file over the server limit gives 413 with a readable message")
    void fileTooLargeGives413() {
        ResponseEntity<Map<String, String>> response =
                handler.fileTooLarge(new MaxUploadSizeExceededException(5L * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(errorOf(response)).contains("too large").contains("5 MB");
    }

    @Test
    @DisplayName("A missing request parameter gives 400 and names the parameter")
    void missingParameterGives400() {
        ResponseEntity<Map<String, String>> response =
                handler.missingParameter(new MissingServletRequestParameterException("jd", "String"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorOf(response)).contains("'jd'").contains("missing");
    }

    @Test
    @DisplayName("A missing file part gives 400 and names the part")
    void missingFilePartGives400() {
        ResponseEntity<Map<String, String>> response =
                handler.missingPart(new MissingServletRequestPartException("file"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorOf(response)).contains("'file'").contains("missing");
    }
}
