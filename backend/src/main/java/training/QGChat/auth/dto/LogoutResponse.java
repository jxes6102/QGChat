package training.QGChat.auth.dto;

// 登出結果，表示目前 token 是否成功被撤銷。
public record LogoutResponse(
        boolean loggedOut
) {
}
