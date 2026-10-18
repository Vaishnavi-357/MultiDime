package com.multidime.validation;

/**
 * Thrown when an uploaded file is accepted by the validator but its text cannot be read.
 * The message is safe to show to the user.
 */
public class UnreadableFileException extends RuntimeException {

    public UnreadableFileException(String message) {
        super(message);
    }
}
