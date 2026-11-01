package com.srikumar.multitenantcrm.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ContactRequest(@NotBlank String name, @Email String email, String phone) {
}
