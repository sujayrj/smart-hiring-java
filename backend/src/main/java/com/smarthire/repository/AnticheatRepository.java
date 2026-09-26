package com.smarthire.repository;

import com.smarthire.domain.AnticheatEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnticheatRepository extends JpaRepository<AnticheatEvent, Long> {
    List<AnticheatEvent> findByApplicationId(Long applicationId);
}
