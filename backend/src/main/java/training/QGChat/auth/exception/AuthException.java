package training.QGChat.auth.exception;

import org.springframework.http.HttpStatus;

// 帶有 HTTP 狀態碼的業務例外，供 controller 統一轉成 API 錯誤回應。
public class AuthException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public AuthException(HttpStatus status, String message) {
        this(status, "UNKNOWN_ERROR", message);
    }

    public AuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
