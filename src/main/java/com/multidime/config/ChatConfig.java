package com.multidime.config;

import java.io.File;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.multidime.advisor.LoggingAdvisor;
import com.multidime.rag.RagIndexer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class ChatConfig {

    // Only the logging advisor is a default. The RAG advisor is added per request in /api/rag/chat.
    @Bean
    ChatClient chatClient(ChatClient.Builder chatClientBuilder, LoggingAdvisor loggingAdvisor) {
        return chatClientBuilder
                .defaultAdvisors(loggingAdvisor)
                .build();
    }

    @Bean
    QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore, SearchRequest searchRequest) {
        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .build();
    }

    /**
     * The vector store holds the embedded chunks of the PDFs.
     *
     * - If the saved store file exists, it is loaded (fast, no API calls).
     * - Otherwise the PDFs are indexed on a background thread, so the app starts straight away.
     *   See RagIndexer for how the indexing is paced to fit the free Gemini quota.
     *
     * To rebuild the index (for example after changing app.rag.max-chunks-per-file), delete the
     * store file and start the app again.
     */
    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel,
                            @Value("${app.rag.input-dir:src/main/resources/input-data}") String inputDir,
                            @Value("${app.rag.store-file:src/main/resources/output-data/vectorstore.json}") String storePath,
                            @Value("${app.rag.max-chunks-per-file:150}") int maxChunksPerFile,
                            @Value("${app.rag.batch-size:10}") int batchSize,
                            @Value("${app.rag.pause-seconds:25}") int pauseSeconds) {

        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        File storeFile = new File(storePath);

        if (storeFile.exists() && storeFile.length() > 5000) {
            try {
                vectorStore.load(storeFile);
                log.info("Loaded the saved vector store from {} ({} KB). To rebuild it, delete that file and restart.",
                        storeFile.getPath(), storeFile.length() / 1024);
                return vectorStore;
            } catch (Exception e) {
                log.warn("Could not load {} ({}). It will be rebuilt from the input files.",
                        storeFile.getPath(), e.getMessage());
            }
        }

        RagIndexer indexer = new RagIndexer(vectorStore, new File(inputDir), storeFile,
                maxChunksPerFile, batchSize, pauseSeconds * 1000L);
        Thread thread = new Thread(indexer::run, "rag-indexer");
        thread.setDaemon(true);
        thread.start();

        return vectorStore;
    }

    @Bean
    SearchRequest searchRequest(@Value("${app.rag.top-k:5}") int topK,
                                @Value("${app.rag.similarity-threshold:0.5}") double similarityThreshold) {
        return SearchRequest.builder()
                .similarityThreshold(similarityThreshold)
                .topK(topK)
                .build();
    }
}
