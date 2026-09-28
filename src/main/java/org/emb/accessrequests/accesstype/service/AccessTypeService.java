package org.emb.accessrequests.accesstype.service;

import java.util.List;

import org.emb.accessrequests.accesstype.dto.AccessTypeDto;

/** The accesses a user can request. */
public interface AccessTypeService {

    /** Every access type, ordered by id. */
    List<AccessTypeDto> listAll();
}
