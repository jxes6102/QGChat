package training.QGChat.auth.dto;

// 統一 API 錯誤格式：code 給程式判斷，message 給畫面顯示。
public record ApiErrorResponse(
        String code,
        String message
) {
}
