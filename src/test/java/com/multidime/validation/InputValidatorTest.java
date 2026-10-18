package com.multidime.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Unit tests for InputValidator. No Spring context, database or Gemini needed.
 */
class InputValidatorTest {

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private static MockMultipartFile file(String name) {
        return file(name, "some text".getBytes());
    }

    // ---------- text fields ----------

    @Test
    @DisplayName("checkMessage rejects null, empty and blank messages")
    void messageMustNotBeEmpty() {
        assertThat(InputValidator.checkMessage(null)).isEqualTo("Message cannot be empty");
        assertThat(InputValidator.checkMessage("")).isEqualTo("Message cannot be empty");
        assertThat(InputValidator.checkMessage("   \n ")).isEqualTo("Message cannot be empty");
    }

    @Test
    @DisplayName("checkMessage accepts exactly the maximum length and rejects one more")
    void messageLengthBoundary() {
        assertThat(InputValidator.checkMessage("a".repeat(InputValidator.MAX_MESSAGE_CHARS))).isNull();
        assertThat(InputValidator.checkMessage("a".repeat(InputValidator.MAX_MESSAGE_CHARS + 1)))
                .contains("too long")
                .contains(String.valueOf(InputValidator.MAX_MESSAGE_CHARS));
    }

    @Test
    @DisplayName("checkQuestion uses the word 'Question' in its message")
    void questionUsesItsOwnLabel() {
        assertThat(InputValidator.checkQuestion(" ")).isEqualTo("Question cannot be empty");
        assertThat(InputValidator.checkQuestion("What is Java?")).isNull();
    }

    @Test
    @DisplayName("checkJobDescription rejects blank text and text over the limit")
    void jobDescriptionRules() {
        assertThat(InputValidator.checkJobDescription(" ")).isEqualTo("Job description cannot be empty");
        assertThat(InputValidator.checkJobDescription("Java developer")).isNull();
        assertThat(InputValidator.checkJobDescription("a".repeat(InputValidator.MAX_JOB_DESCRIPTION_CHARS))).isNull();
        assertThat(InputValidator.checkJobDescription("a".repeat(InputValidator.MAX_JOB_DESCRIPTION_CHARS + 1)))
                .contains("too long");
    }

    // ---------- files ----------

    @Test
    @DisplayName("checkFile rejects a missing or empty file")
    void fileMustNotBeEmpty() {
        assertThat(InputValidator.checkFile(null)).contains("empty");
        assertThat(InputValidator.checkFile(file("resume.pdf", new byte[0]))).contains("empty");
    }

    @Test
    @DisplayName("checkFile accepts the allowed file types")
    void allowedTypesPass() {
        for (String name : new String[] {"cv.pdf", "cv.doc", "cv.docx", "cv.txt", "cv.rtf"}) {
            assertThat(InputValidator.checkFile(file(name))).as(name).isNull();
        }
    }

    @Test
    @DisplayName("checkFile ignores the case of the file extension")
    void extensionCaseIsIgnored() {
        assertThat(InputValidator.checkFile(file("CV.PDF"))).isNull();
        assertThat(InputValidator.checkFile(file("Resume.Docx"))).isNull();
    }

    @Test
    @DisplayName("checkFile rejects other file types and files with no extension")
    void otherTypesAreRejected() {
        assertThat(InputValidator.checkFile(file("virus.exe"))).contains("Unsupported file type");
        assertThat(InputValidator.checkFile(file("photo.png"))).contains("Unsupported file type");
        assertThat(InputValidator.checkFile(file("resume"))).contains("Unsupported file type");
    }

    @Test
    @DisplayName("checkFile uses the last extension, so 'cv.pdf.exe' is rejected")
    void lastExtensionDecides() {
        assertThat(InputValidator.checkFile(file("cv.pdf.exe"))).contains("Unsupported file type");
    }

    @Test
    @DisplayName("checkFile accepts a file of exactly the maximum size and rejects one byte more")
    void fileSizeBoundary() {
        byte[] exactly = new byte[(int) InputValidator.MAX_FILE_BYTES];
        byte[] oneMore = new byte[(int) InputValidator.MAX_FILE_BYTES + 1];

        assertThat(InputValidator.checkFile(file("cv.pdf", exactly))).isNull();
        assertThat(InputValidator.checkFile(file("cv.pdf", oneMore))).contains("too large").contains("5 MB");
    }

    // ---------- helper ----------

    @Test
    @DisplayName("extensionOf returns the lower-case part after the last dot")
    void extensionOfHelper() {
        assertThat(InputValidator.extensionOf("My.Resume.PDF")).isEqualTo("pdf");
        assertThat(InputValidator.extensionOf("noextension")).isEmpty();
        assertThat(InputValidator.extensionOf(null)).isEmpty();
    }
}
