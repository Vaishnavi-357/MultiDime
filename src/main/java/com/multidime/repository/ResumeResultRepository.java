package com.multidime.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.multidime.model.ResumeResult;

public interface ResumeResultRepository extends JpaRepository<ResumeResult, Long> {
}
