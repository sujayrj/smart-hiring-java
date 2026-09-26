package com.smarthire.repository;

import com.smarthire.domain.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    List<Candidate> findByAppliedJd(Long jdId);
    Optional<Candidate> findByUserId(Long userId);
}
