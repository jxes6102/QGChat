package training.QGChat.auth.dto;

// 忘記密碼流程的接受回應；resetToken 主要供本機或測試環境使用。
public record ForgotPasswordResponse(
        String message,
        String resetToken
) {
    public static ForgotPasswordResponse accepted(String resetToken) {
        // 固定訊息避免洩漏 email 是否存在。
        return new ForgotPasswordResponse("If the email exists, password reset instructions will be sent.", resetToken);
    }
}
