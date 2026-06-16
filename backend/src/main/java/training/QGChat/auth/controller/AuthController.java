package training.QGChat.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import training.QGChat.auth.dto.ApiErrorResponse;
import training.QGChat.auth.dto.AuthResponse;
import training.QGChat.auth.dto.ForgotPasswordRequest;
import training.QGChat.auth.dto.ForgotPasswordResponse;
import training.QGChat.auth.dto.LoginRequest;
import training.QGChat.auth.dto.LogoutResponse;
import training.QGChat.auth.dto.RegisterRequest;
import training.QGChat.auth.dto.ResetPasswordRequest;
import training.QGChat.auth.dto.ResetPasswordResponse;
import training.QGChat.auth.exception.AuthException;
import training.QGChat.auth.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        // 註冊成功會直接建立登入 session，因此需要紀錄來源 IP 與 User-Agent。
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, clientIp(servletRequest), servletRequest.getHeader("User-Agent")));
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        // 登入後回傳 bearer token，前端後續 API 需放在 Authorization header。
        return authService.login(request, clientIp(servletRequest), servletRequest.getHeader("User-Agent"));
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        // 忘記密碼固定回 accepted，避免外部藉由回應判斷 email 是否存在。
        return authService.forgotPassword(request, clientIp(servletRequest), servletRequest.getHeader("User-Agent"));
    }

    @PostMapping("/reset-password")
    public ResetPasswordResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    @PostMapping("/logout")
    public LogoutResponse logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.logout(authorization);
    }

    // 將認證流程丟出的業務錯誤統一轉成 JSON，讓前端可以穩定讀取 code 與中文 message。
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(AuthException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiErrorResponse(exception.code(), exception.getMessage()));
    }

    private String clientIp(HttpServletRequest request) {
        // 若服務部署在反向代理後方，優先採用 X-Forwarded-For 的第一個 IP。
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
