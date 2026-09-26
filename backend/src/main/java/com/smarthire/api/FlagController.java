package com.smarthire.api;

import com.smarthire.service.FlagService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FlagController {

    public record AnticheatRequest(Long applicationId, String eventType, String payload) {}

    private final FlagService flagService;

    public FlagController(FlagService flagService) {
        this.flagService = flagService;
    }

    /** §12: Admin-only flag view. */
    @GetMapping("/flags")
    @PreAuthorize("hasRole('ADMIN')")
    public List<FlagService.FlagView> flags() {
        return flagService.computeFlags();
    }

    /** §12: browser telemetry capture (tab-switch / paste). Called by candidate SPA. */
    @PostMapping("/anticheat")
    @PreAuthorize("hasRole('CANDIDATE')")
    public void report(@RequestBody AnticheatRequest req) {
        flagService.recordEvent(req.applicationId(), req.eventType(), req.payload());
    }
}
