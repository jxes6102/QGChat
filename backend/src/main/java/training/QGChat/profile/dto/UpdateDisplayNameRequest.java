package training.QGChat.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDisplayNameRequest(
        @NotBlank
        @Size(max = 80)
        String displayName
) {
}
