package com.multidime.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the small helper methods of RagIndexer. They use a temporary folder, so they need
 * neither Gemini nor MySQL.
 *
 * These matter because resuming an interrupted run only works if the files are always read in
 * the same order and the saved progress is only trusted for the same input files.
 */
class RagIndexerTest {

    @TempDir
    Path tmp;

    private File write(String name, String content) throws IOException {
        Path file = tmp.resolve(name);
        Files.writeString(file, content);
        return file.toFile();
    }

    // ---------- listInputFiles ----------

    @Test
    @DisplayName("listInputFiles returns the files in name order and skips sub-folders")
    void listsFilesInNameOrderAndSkipsFolders() throws IOException {
        write("b.pdf", "second");
        write("a.pdf", "first");
        write("c.pdf", "third");
        Files.createDirectory(tmp.resolve("sub-folder"));

        List<File> files = RagIndexer.listInputFiles(tmp.toFile());

        assertThat(files).extracting(File::getName).containsExactly("a.pdf", "b.pdf", "c.pdf");
    }

    @Test
    @DisplayName("listInputFiles gives an empty list when the folder does not exist")
    void missingFolderGivesEmptyList() {
        File missing = tmp.resolve("does-not-exist").toFile();

        assertThat(RagIndexer.listInputFiles(missing)).isEmpty();
    }

    // ---------- fingerprint ----------

    @Test
    @DisplayName("fingerprint is the same for the same files and limit")
    void fingerprintIsStable() throws IOException {
        List<File> files = List.of(write("a.txt", "one"), write("b.txt", "two"));

        assertThat(RagIndexer.fingerprint(files, 150)).isEqualTo(RagIndexer.fingerprint(files, 150));
    }

    @Test
    @DisplayName("fingerprint changes when a file is edited")
    void fingerprintChangesWhenAFileChanges() throws IOException {
        File file = write("a.txt", "one");
        String before = RagIndexer.fingerprint(List.of(file), 150);

        Files.writeString(file.toPath(), "one two three");
        String after = RagIndexer.fingerprint(List.of(file), 150);

        assertThat(after).isNotEqualTo(before);
    }

    @Test
    @DisplayName("fingerprint changes when a file is added")
    void fingerprintChangesWhenAFileIsAdded() throws IOException {
        File first = write("a.txt", "one");
        File second = write("b.txt", "two");

        assertThat(RagIndexer.fingerprint(List.of(first, second), 150))
                .isNotEqualTo(RagIndexer.fingerprint(List.of(first), 150));
    }

    @Test
    @DisplayName("fingerprint changes when the chunk limit changes")
    void fingerprintChangesWhenLimitChanges() throws IOException {
        List<File> files = List.of(write("a.txt", "one"));

        assertThat(RagIndexer.fingerprint(files, 150)).isNotEqualTo(RagIndexer.fingerprint(files, 0));
    }

    // ---------- saved progress ----------

    @Test
    @DisplayName("saved progress can be read back for the same input files")
    void progressRoundTrip() throws IOException {
        File progress = tmp.resolve("store.json.progress").toFile();

        RagIndexer.writeProgress(progress, 40, "a.txt:3;max=150");

        assertThat(RagIndexer.readProgress(progress, "a.txt:3;max=150")).isEqualTo(40);
    }

    @Test
    @DisplayName("saved progress is ignored when the input files have changed")
    void progressIgnoredWhenInputsChanged() throws IOException {
        File progress = tmp.resolve("store.json.progress").toFile();
        RagIndexer.writeProgress(progress, 40, "a.txt:3;max=150");

        assertThat(RagIndexer.readProgress(progress, "a.txt:9;max=150")).isNull();
    }

    @Test
    @DisplayName("there is no progress when the progress file is missing")
    void noProgressWhenFileMissing() {
        File progress = tmp.resolve("missing.progress").toFile();

        assertThat(RagIndexer.readProgress(progress, "anything")).isNull();
    }

    @Test
    @DisplayName("a damaged progress file is ignored instead of causing an error")
    void damagedProgressFileIsIgnored() throws IOException {
        File progress = write("store.json.progress", "not a number\nfingerprint");

        assertThat(RagIndexer.readProgress(progress, "fingerprint")).isNull();
    }
}
