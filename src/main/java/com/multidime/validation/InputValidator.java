package com.multidime.validation;

import java.util.Locale;
import java.util.Set;

import org.springframework.web.multipart.MultipartFile;

/**
 * The rules for what the API accepts. Every check returns null when the input is fine,
 * or a short message that is safe to show to the user.
 *
 * Keep MAX_FILE_BYTES in step with spring.servlet.multipart.max-file-size in application.properties.
 */
public final class InputValidator {

    public static final int MAX_MESSAGE_CHARS = 4_000;
    public static final int MAX_JOB_DESCRIPTION_CHARS = 10_000;
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt", "rtf");

    private InputValidator() {
    }

    public static String checkMessage(String message) {
        return checkText(message, "Message", MAX_MESSAGE_CHARS);
    }

    public static String checkQuestion(String question) {
        return checkText(question, "Question", MAX_MESSAGE_CHARS);
    }

    public static String checkJobDescription(String jobDescription) {
        return checkText(jobDescription, "Job description", MAX_JOB_DESCRIPTION_CHARS);
    }

    public static String checkFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "The uploaded file is empty. Please choose a resume file.";
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            return "The file is too large (maximum " + (MAX_FILE_BYTES / (1024 * 1024)) + " MB).";
        }
        if (!ALLOWED_EXTENSIONS.contains(extensionOf(file.getOriginalFilename()))) {
            return "Unsupported file type. Please upload a PDF, Word (.doc or .docx), text (.txt) or .rtf file.";
        }
        return null;
    }

    static String checkText(String value, String label, int maxChars) {
        if (value == null || value.trim().isEmpty()) {
            return label + " cannot be empty";
        }
        if (value.length() > maxChars) {
            return label + " is too long (maximum " + maxChars + " characters)";
        }
        return null;
    }

    /** The part after the last dot, in lower case; empty if there is none. */
    static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
    }
}
