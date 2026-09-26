package com.smarthire.repository;

import com.smarthire.domain.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByJdId(Long jdId);
    List<Application> findByCandidateId(Long candidateId);
    Optional<Application> findByCandidateIdAndJdId(Long candidateId, Long jdId);
}
