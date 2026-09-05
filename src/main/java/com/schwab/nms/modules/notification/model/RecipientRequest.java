package com.schwab.nms.modules.notification.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record RecipientRequest(

        @NotBlank
        String recipientKey,

        @Email
        String email,

        String phone,

        List<String> preferredChannels
) {
}