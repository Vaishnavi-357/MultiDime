package com.multidime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.multidime.model.ChatMessage;
import com.multidime.model.ResumeResult;
import com.multidime.repository.ChatMessageRepository;
import com.multidime.repository.ResumeResultRepository;

/**
 * Unit tests for HistoryService. The repositories are mocks, so no database is needed.
 */
class HistoryServiceTest {

    private ChatMessageRepository chats;
    private ResumeResultRepository resumes;
    private HistoryService service;

    @BeforeEach
    void setUp() {
        chats = mock(ChatMessageRepository.class);
        resumes = mock(ResumeResultRepository.class);
        service = new HistoryService(chats, resumes);
    }

    private ResumeResult savedResume() {
        ArgumentCaptor<ResumeResult> captor = ArgumentCaptor.forClass(ResumeResult.class);
        verify(resumes).save(captor.capture());
        return captor.getValue();
    }

    // ---------- saving chats ----------

    @Test
    @DisplayName("saveChat stores the endpoint, question and answer")
    void saveChatStoresEverything() {
        service.saveChat("rag", "What is JVM?", "A virtual machine");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chats).save(captor.capture());
        ChatMessage saved = captor.getValue();
        assertThat(saved.getEndpoint()).isEqualTo("rag");
        assertThat(saved.getQuestion()).isEqualTo("What is JVM?");
        assertThat(saved.getAnswer()).isEqualTo("A virtual machine");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("saveChat does not throw when the database fails")
    void saveChatSwallowsDatabaseErrors() {
        when(chats.save(any(ChatMessage.class))).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.saveChat("chat", "Q", "A")).doesNotThrowAnyException();
    }

    // ---------- saving resume results ----------

    @Test
    @DisplayName("saveResume reads atsScore from the AI's JSON reply for a job match")
    void saveResumeReadsAtsScore() {
        String reply = "```json\n{\"atsScore\": 85, \"matchedKeywords\": [\"Java\"]}\n```";

        service.saveResume("ATS", "cv.pdf", "Java developer wanted", reply);

        ResumeResult saved = savedResume();
        assertThat(saved.getKind()).isEqualTo("ATS");
        assertThat(saved.getFileName()).isEqualTo("cv.pdf");
        assertThat(saved.getJobDescription()).isEqualTo("Java developer wanted");
        assertThat(saved.getScore()).isEqualTo(85.0);
        assertThat(saved.getResultText()).isEqualTo(reply);
    }

    @Test
    @DisplayName("saveResume reads the rating from the AI's JSON reply for a resume review")
    void saveResumeReadsRating() {
        service.saveResume("ANALYZE", "cv.pdf", null, "{\"skills\": [\"Java\"], \"rating\": 7.5}");

        ResumeResult saved = savedResume();
        assertThat(saved.getKind()).isEqualTo("ANALYZE");
        assertThat(saved.getScore()).isEqualTo(7.5);
        assertThat(saved.getJobDescription()).isNull();
    }

    @Test
    @DisplayName("saveResume accepts a score written as text")
    void saveResumeAcceptsTextScore() {
        service.saveResume("ATS", "cv.pdf", "jd", "{\"atsScore\": \"72\"}");

        assertThat(savedResume().getScore()).isEqualTo(72.0);
    }

    @Test
    @DisplayName("saveResume keeps the reply and leaves the score empty when the reply is not JSON")
    void saveResumeKeepsNullScoreWhenReplyIsNotJson() {
        service.saveResume("ATS", "cv.pdf", "jd", "Sorry, I could not read that resume.");

        ResumeResult saved = savedResume();
        assertThat(saved.getScore()).isNull();
        assertThat(saved.getResultText()).isEqualTo("Sorry, I could not read that resume.");
    }

    @Test
    @DisplayName("saveResume does not throw when the database fails")
    void saveResumeSwallowsDatabaseErrors() {
        when(resumes.save(any(ResumeResult.class))).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.saveResume("ATS", "cv.pdf", "jd", "{}")).doesNotThrowAnyException();
    }

    // ---------- reading history ----------

    @Test
    @DisplayName("recentChats asks for the newest first")
    void recentChatsAsksForNewestFirst() {
        ChatMessage message = new ChatMessage("chat", "Q", "A");
        when(chats.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(message)));

        List<ChatMessage> result = service.recentChats(20);

        assertThat(result).containsExactly(message);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(chats).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("recentChats never asks for more than 100 rows")
    void recentChatsCapsTheLimit() {
        when(chats.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.recentChats(500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(chats).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("recentResumes asks for at least one row even if the limit is zero")
    void recentResumesRaisesTooSmallLimit() {
        ResumeResult result = new ResumeResult("ATS", "cv.pdf", "jd", 80.0, "{}");
        when(resumes.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(result)));

        List<ResumeResult> found = service.recentResumes(0);

        assertThat(found).containsExactly(result);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(resumes).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }
}
