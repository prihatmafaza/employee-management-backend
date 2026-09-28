package org.emb.accessrequests.request.controller;

import java.util.List;

import org.emb.accessrequests.auth.security.AppUserDetails;
import org.emb.accessrequests.request.dto.RequestDtos.ReviewItemDto;
import org.emb.accessrequests.request.service.RequestService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final RequestService requestService;

    public ApprovalController(RequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping
    public List<ReviewItemDto> list(@AuthenticationPrincipal AppUserDetails principal) {
        return requestService.listReviewItems(principal.user());
    }
}
