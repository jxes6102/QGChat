package training.QGChat.auth.dto;

public record ForgotPasswordResponse(
        String message,
        String resetToken
) {
    public static ForgotPasswordResponse accepted(String resetToken) {
        return new ForgotPasswordResponse("If the email exists, password reset instructions will be sent.", resetToken);
    }
}
