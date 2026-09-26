package com.smarthire.repository;

import com.smarthire.domain.Interview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InterviewRepository extends JpaRepository<Interview, Long> {
    List<Interview> findByInterviewerId(Long interviewerId);
    Optional<Interview> findByApplicationId(Long applicationId);
}
