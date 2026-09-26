package com.smarthire.service;

import com.smarthire.domain.Role;
import com.smarthire.domain.User;
import com.smarthire.repository.UserRepository;
import com.smarthire.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Auth (§10): validate hard-coded seed credentials, issue signed JWT, derive role.
 * Demo-grade by explicit design.
 */
@Service
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepo, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record LoginResponse(String token, String role, Long userId, String username) {}

    public LoginResponse login(String username, String password) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        String token = jwtService.issue(user.getId(), user.getUsername(), user.getRole().name());
        return new LoginResponse(token, user.getRole().name(), user.getId(), user.getUsername());
    }
}
