package com.smarthire.repository;

import com.smarthire.domain.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    List<Answer> findByApplicationId(Long applicationId);
}
