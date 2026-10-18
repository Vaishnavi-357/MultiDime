package com.multidime.rag;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.FileSystemResource;

/**
 * Reads the files in the input folder, cuts them into chunks and stores each chunk's embedding
 * in the vector store.
 *
 * Why it works this way:
 * - The free Gemini embedding quota is small (limits are per minute and per day), so chunks are
 *   embedded in small batches with a pause in between instead of all at once.
 * - Progress is saved after every batch. If the app is stopped or the quota runs out, the next
 *   start continues from where it stopped instead of paying for the same chunks again.
 * - It is meant to run on a background thread, so the app can start straight away.
 *
 * Files used next to the store file (for example vectorstore.json):
 *   vectorstore.json.partial   the chunks embedded so far
 *   vectorstore.json.progress  how many chunks that is, plus a fingerprint of the input files
 * Both are deleted when the index is complete.
 */
public class RagIndexer {

    private static final Logger log = LoggerFactory.getLogger(RagIndexer.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_WAIT_MILLIS = 60_000L;

    private final SimpleVectorStore store;
    private final File inputDir;
    private final File storeFile;
    private final File partialFile;
    private final File progressFile;
    private final int maxChunksPerFile;
    private final int batchSize;
    private final long pauseMillis;

    /**
     * @param maxChunksPerFile most chunks to index from one file; 0 or less means no limit
     * @param batchSize        chunks embedded before each pause
     * @param pauseMillis      pause between batches
     */
    public RagIndexer(SimpleVectorStore store, File inputDir, File storeFile,
                      int maxChunksPerFile, int batchSize, long pauseMillis) {
        this.store = store;
        this.inputDir = inputDir;
        this.storeFile = storeFile;
        this.partialFile = new File(storeFile.getPath() + ".partial");
        this.progressFile = new File(storeFile.getPath() + ".progress");
        this.maxChunksPerFile = maxChunksPerFile;
        this.batchSize = Math.max(1, batchSize);
        this.pauseMillis = Math.max(0, pauseMillis);
    }

    /** Runs the whole indexing job. Never throws; problems are written to the log. */
    public void run() {
        try {
            index();
        } catch (Exception e) {
            log.error("Indexing stopped because of an error: {}", e.getMessage(), e);
        }
    }

    private void index() throws IOException {
        List<File> files = listInputFiles(inputDir);
        if (files.isEmpty()) {
            log.warn("No files found in {}, so there is nothing to index.", inputDir.getAbsolutePath());
            return;
        }

        File parent = storeFile.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        List<Document> chunks = readAndSplit(files);
        if (chunks.isEmpty()) {
            log.warn("No text could be read from the files in {}.", inputDir.getAbsolutePath());
            return;
        }

        String fingerprint = fingerprint(files, maxChunksPerFile);
        int done = 0;
        Integer saved = readProgress(progressFile, fingerprint);
        if (saved != null && saved < chunks.size() && partialFile.isFile()) {
            try {
                store.load(partialFile);
                done = saved;
                log.info("Continuing an earlier run: {} of {} chunks are already embedded.", done, chunks.size());
            } catch (Exception e) {
                log.warn("Could not read the earlier progress ({}). Starting again.", e.getMessage());
            }
        }

        log.info("Indexing {} chunks from {} files in batches of {}. Questions can be answered "
                + "from the part that is already indexed while this runs.", chunks.size(), files.size(), batchSize);

        for (int from = done; from < chunks.size(); from += batchSize) {
            List<Document> batch = new ArrayList<>(chunks.subList(from, Math.min(from + batchSize, chunks.size())));
            if (!addWithRetry(batch)) {
                log.error("Stopped after {} of {} chunks. Start the app again later (for example after the "
                        + "daily quota resets) and it will continue from here.", from, chunks.size());
                return;
            }
            done = from + batch.size();
            store.save(partialFile);
            writeProgress(progressFile, done, fingerprint);
            log.info("Indexed {} of {} chunks.", done, chunks.size());

            if (done < chunks.size() && !pause(pauseMillis)) {
                return;
            }
        }

        store.save(storeFile);
        Files.deleteIfExists(partialFile.toPath());
        Files.deleteIfExists(progressFile.toPath());
        log.info("Indexing complete: {} chunks saved to {} ({} KB).", chunks.size(), storeFile.getPath(),
                storeFile.length() / 1024);
    }

    private List<Document> readAndSplit(List<File> files) {
        List<Document> all = new ArrayList<>();
        for (File file : files) {
            try {
                List<Document> raw = new TikaDocumentReader(new FileSystemResource(file)).get();
                List<Document> chunks = new TokenTextSplitter().apply(raw);
                int total = chunks.size();
                if (maxChunksPerFile > 0 && total > maxChunksPerFile) {
                    chunks = chunks.subList(0, maxChunksPerFile);
                }
                all.addAll(chunks);
                log.info("{}: using {} of {} chunks.", file.getName(), chunks.size(), total);
            } catch (Exception e) {
                log.error("Could not read {}: {}", file.getName(), e.getMessage());
            }
        }
        return all;
    }

    /** Embeds one batch. Waits and tries again if the API refuses (for example a rate limit). */
    private boolean addWithRetry(List<Document> batch) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                store.add(batch);
                return true;
            } catch (Exception e) {
                log.warn("A batch failed (attempt {} of {}): {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS && !pause(RETRY_WAIT_MILLIS)) {
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean pause(long millis) {
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ---------- small helpers (package-private so they can be unit tested) ----------

    /** The regular files in the folder, in a fixed order so that a resumed run sees the same chunks. */
    static List<File> listInputFiles(File dir) {
        File[] found = dir.listFiles(f -> f.isFile() && !f.isHidden());
        if (found == null) {
            return new ArrayList<>();
        }
        List<File> files = new ArrayList<>(Arrays.asList(found));
        files.sort(Comparator.comparing(File::getName));
        return files;
    }

    /** Changes whenever a file is added, removed or edited, or the per-file limit changes. */
    static String fingerprint(List<File> files, int maxChunksPerFile) {
        StringBuilder sb = new StringBuilder();
        for (File f : files) {
            sb.append(f.getName()).append(':').append(f.length()).append(';');
        }
        return sb.append("max=").append(maxChunksPerFile).toString();
    }

    /** The saved chunk count, or null if there is no usable progress for these input files. */
    static Integer readProgress(File progressFile, String fingerprint) {
        try {
            if (!progressFile.isFile()) {
                return null;
            }
            List<String> lines = Files.readAllLines(progressFile.toPath(), StandardCharsets.UTF_8);
            if (lines.size() < 2 || !lines.get(1).equals(fingerprint)) {
                return null;
            }
            int done = Integer.parseInt(lines.get(0).trim());
            return done > 0 ? done : null;
        } catch (Exception e) {
            return null;
        }
    }

    static void writeProgress(File progressFile, int done, String fingerprint) throws IOException {
        Files.writeString(progressFile.toPath(), done + "\n" + fingerprint, StandardCharsets.UTF_8);
    }
}
