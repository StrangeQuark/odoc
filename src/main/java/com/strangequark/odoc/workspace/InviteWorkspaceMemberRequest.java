package com.strangequark.odoc.workspace;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** First local collaboration flow: invite an existing local account by email. */
public record InviteWorkspaceMemberRequest(
        @NotBlank @Email @Size(max = 320) String email) {}
