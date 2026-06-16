package training.QGChat.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateGroupMemberRoleRequest(
        @NotBlank
        @Pattern(regexp = "ADMIN|MEMBER")
        String role
) {
}
