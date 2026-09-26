package com.smarthire.repository;

import com.smarthire.domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByJdIdOrderByOrderIndex(Long jdId);
}
