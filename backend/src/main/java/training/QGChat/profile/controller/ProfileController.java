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
import training.QGChat.auth.exception.AuthException;
import training.QGChat.profile.dto.ChangeEmailRequest;
import training.QGChat.profile.dto.ChangePasswordRequest;
import training.QGChat.profile.dto.UpdateAvatarRequest;
import training.QGChat.profile.dto.UpdateDisplayNameRequest;
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
        return profileService.getProfile(authorization);
    }

    @PatchMapping
    public UserProfileResponse updateProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return profileService.updateProfile(authorization, request);
    }

    @PatchMapping("/display-name")
    public UserProfileResponse updateDisplayName(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpdateDisplayNameRequest request
    ) {
        return profileService.updateDisplayName(authorization, request);
    }

    @PatchMapping("/avatar")
    public UserProfileResponse updateAvatar(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpdateAvatarRequest request
    ) {
        return profileService.updateAvatar(authorization, request);
    }

    @PatchMapping("/email")
    public UserProfileResponse changeEmail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ChangeEmailRequest request
    ) {
        return profileService.changeEmail(authorization, request);
    }

    @PatchMapping("/password")
    public Map<String, Boolean> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        return Map.of("passwordChanged", profileService.changePassword(authorization, request));
    }

    // 將會員資料相關錯誤統一轉成 JSON 格式回傳給前端。
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuthException(AuthException exception) {
        return ResponseEntity.status(exception.status())
                .body(Map.of("message", exception.getMessage()));
    }
}
