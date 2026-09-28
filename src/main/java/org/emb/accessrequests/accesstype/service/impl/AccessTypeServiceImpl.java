package org.emb.accessrequests.accesstype.service.impl;

import java.util.List;

import org.emb.accessrequests.accesstype.dto.AccessTypeDto;
import org.emb.accessrequests.accesstype.repository.AccessTypeRepository;
import org.emb.accessrequests.accesstype.service.AccessTypeService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessTypeServiceImpl implements AccessTypeService {

    private final AccessTypeRepository accessTypes;

    public AccessTypeServiceImpl(AccessTypeRepository accessTypes) {
        this.accessTypes = accessTypes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccessTypeDto> listAll() {
        return accessTypes.findAll(Sort.by("id")).stream().map(AccessTypeDto::from).toList();
    }
}
