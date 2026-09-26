package com.smarthire.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * Requirement: "Resume Auto-scoring - invoked as a batch job that can run at specific
 * intervals and process a batch of JDs."
 *
 * Disabled by default (app.batch.enabled=false) so the demo stays deterministic —
 * Admin triggers scoring manually, or via POST /api/jds/resume-score-all.
 * Enable with APP_BATCH_ENABLED=true; interval via APP_BATCH_INTERVAL_MS.
 * Runs outside any request → audit actor is SYSTEM.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.batch.enabled", havingValue = "true")
public class SchedulingConfig {

    private final com.smarthire.service.ScreeningService screeningService;
    private final com.smarthire.repository.JdRepository jdRepo;

    public SchedulingConfig(com.smarthire.service.ScreeningService screeningService,
                            com.smarthire.repository.JdRepository jdRepo) {
        this.screeningService = screeningService;
        this.jdRepo = jdRepo;
    }

    @Scheduled(fixedDelayString = "${app.batch.interval-ms:300000}", initialDelay = 20000)
    public void scoreAllJds() {
        jdRepo.findAll().forEach(jd -> screeningService.screenJd(jd.getId()));
    }
}
