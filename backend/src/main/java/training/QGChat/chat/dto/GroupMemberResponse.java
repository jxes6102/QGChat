package training.QGChat.chat.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

// 群組成員管理頁使用的成員資料，包含角色與是否為目前登入者。
public record GroupMemberResponse(
        // 使用者 id，前端操作升降權、移除、轉移擁有者時會帶回後端。
        UUID userId,
        // 帳號名稱，用於搜尋、顯示與新增成員時的對照。
        String username,
        // 顯示名稱，作為成員清單的主要名稱。
        String displayName,
        // 成員頭像網址，前端可選擇性顯示。
        String avatarUrl,
        // 群組角色：OWNER、ADMIN 或 MEMBER。
        String role,
        // 加入群組時間，用於後續需要排序或顯示加入資訊。
        OffsetDateTime joinedAt,
        // 標記這筆成員是否為目前登入者，方便前端隱藏自我移除等操作。
        boolean currentUser
) {
}
