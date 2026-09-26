package com.smarthire.api;

import com.smarthire.service.AccessService;
import com.smarthire.service.AuditService;
import com.smarthire.service.AuthService;
import com.smarthire.security.JwtService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthService.LoginResponse login(@RequestBody LoginRequest req) {
        return authService.login(req.username(), req.password());
    }
}
