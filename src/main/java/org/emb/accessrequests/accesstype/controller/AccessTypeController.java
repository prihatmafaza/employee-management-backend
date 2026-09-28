package org.emb.accessrequests.accesstype.controller;

import java.util.List;

import org.emb.accessrequests.accesstype.dto.AccessTypeDto;
import org.emb.accessrequests.accesstype.service.AccessTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/access-types")
public class AccessTypeController {

    private final AccessTypeService accessTypeService;

    public AccessTypeController(AccessTypeService accessTypeService) {
        this.accessTypeService = accessTypeService;
    }

    @GetMapping
    public List<AccessTypeDto> list() {
        return accessTypeService.listAll();
    }
}
