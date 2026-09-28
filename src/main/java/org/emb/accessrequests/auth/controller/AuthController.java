package org.emb.accessrequests.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.emb.accessrequests.auth.dto.LoginBody;
import org.emb.accessrequests.auth.dto.LoginResponse;
import org.emb.accessrequests.auth.security.AppUserDetails;
import org.emb.accessrequests.auth.service.AuthService;
import org.emb.accessrequests.user.dto.UserDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginBody body, HttpServletRequest request) {
        return authService.login(body, request);
    }

    /** Always 204, with or without a token, so logging out is always safe. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        authService.logout(request);
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AppUserDetails principal) {
        return authService.me(principal);
    }
}
