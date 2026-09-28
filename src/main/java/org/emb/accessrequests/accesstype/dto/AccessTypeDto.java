package org.emb.accessrequests.accesstype.dto;

import org.emb.accessrequests.accesstype.entity.AccessType;

public record AccessTypeDto(long id, String name, String description) {

    public static AccessTypeDto from(AccessType type) {
        return new AccessTypeDto(type.getId(), type.getName(), type.getDescription());
    }
}
