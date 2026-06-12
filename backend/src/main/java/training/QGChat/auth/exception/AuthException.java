package training.QGChat.auth.exception;

import org.springframework.http.HttpStatus;

// 帶有 HTTP 狀態碼的業務例外，供 controller 統一轉成 API 錯誤回應。
public class AuthException extends RuntimeException {
    private final HttpStatus status;

    public AuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
