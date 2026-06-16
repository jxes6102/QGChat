package training.QGChat.profile.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import training.QGChat.auth.dto.ApiErrorResponse;
import training.QGChat.auth.exception.AuthException;
import training.QGChat.profile.dto.ChangePasswordRequest;
import training.QGChat.profile.dto.UpdateProfileRequest;
import training.QGChat.profile.dto.UserProfileResponse;
import training.QGChat.profile.service.ProfileService;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public UserProfileResponse getProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        // 依 bearer token 取得目前登入使用者的個人資料。
        return profileService.getProfile(authorization);
    }

    @PatchMapping
    public UserProfileResponse updateProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        // 支援部分更新：只更新 request 中有帶入的欄位。
        return profileService.updateProfile(authorization, request);
    }

    @PatchMapping("/password")
    public Map<String, Boolean> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        // 修改密碼成功後，service 會撤銷其他登入 session。
        return Map.of("passwordChanged", profileService.changePassword(authorization, request));
    }

    // 將個人資料流程的業務錯誤統一轉成 JSON。
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(AuthException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiErrorResponse(exception.code(), exception.getMessage()));
    }
}
