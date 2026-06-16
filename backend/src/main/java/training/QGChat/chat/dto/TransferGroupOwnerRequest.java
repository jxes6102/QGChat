package training.QGChat.chat.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferGroupOwnerRequest(
        @NotNull UUID newOwnerUserId
) {
}
