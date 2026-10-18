package com.multidime.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.multidime.validation.InputValidator;

/**
 * Turns the errors Spring raises before our controller code runs into the same
 * {"error": "..."} shape the controller uses, so the frontend only has to handle one format.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> fileTooLarge(MaxUploadSizeExceededException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE,
                "The file is too large (maximum " + (InputValidator.MAX_FILE_BYTES / (1024 * 1024)) + " MB).");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> missingParameter(MissingServletRequestParameterException e) {
        return error(HttpStatus.BAD_REQUEST, "Required parameter '" + e.getParameterName() + "' is missing.");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, String>> missingPart(MissingServletRequestPartException e) {
        return error(HttpStatus.BAD_REQUEST, "Required file part '" + e.getRequestPartName() + "' is missing.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadableBody(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "The request body is missing or is not valid JSON.");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
