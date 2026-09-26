package com.smarthire.repository;

import com.smarthire.domain.JobDescription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JdRepository extends JpaRepository<JobDescription, Long> {}
