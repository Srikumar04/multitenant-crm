package com.srikumar.multitenantcrm.contact;

import java.time.Instant;
import java.util.UUID;

public record ContactResponse(
        UUID id, String name, String email, String phone, UUID createdBy, Instant createdAt) {

    public static ContactResponse from(Contact contact) {
        return new ContactResponse(
                contact.getId(),
                contact.getName(),
                contact.getEmail(),
                contact.getPhone(),
                contact.getCreatedBy(),
                contact.getCreatedAt());
    }
}
