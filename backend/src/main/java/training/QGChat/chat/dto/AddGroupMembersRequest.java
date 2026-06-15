package training.QGChat.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record AddGroupMembersRequest(
        @NotEmpty Set<@NotBlank @Size(max = 50) String> memberUsernames
) {
}
