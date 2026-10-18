package com.multidime.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * The result of one resume review (/api/analyze) or one job match (/api/ats-check).
 */
@Entity
@Table(name = "resume_result")
public class ResumeResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "ANALYZE" for a resume review, "ATS" for a job match */
    @Column(nullable = false, length = 20)
    private String kind;

    @Column(name = "file_name", length = 255)
    private String fileName;

    /** Only filled for "ATS" */
    @Lob
    @Column(name = "job_description")
    private String jobDescription;

    /** The rating (out of 10) or the ATS score (out of 100); null if the AI reply had no number. */
    private Double score;

    /** The AI's reply exactly as received. */
    @Lob
    @Column(name = "result_text", nullable = false)
    private String resultText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ResumeResult() {
        // required by JPA
    }

    public ResumeResult(String kind, String fileName, String jobDescription, Double score, String resultText) {
        this.kind = kind;
        this.fileName = fileName;
        this.jobDescription = jobDescription;
        this.score = score;
        this.resultText = resultText;
    }

    public Long getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    public String getFileName() {
        return fileName;
    }

    public String getJobDescription() {
        return jobDescription;
    }

    public Double getScore() {
        return score;
    }

    public String getResultText() {
        return resultText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
