package org.emb.accessrequests.request.controller;

import java.util.List;

import org.emb.accessrequests.auth.security.AppUserDetails;
import org.emb.accessrequests.request.dto.RequestDtos.AccessRequestDto;
import org.emb.accessrequests.request.dto.RequestDtos.DecisionBody;
import org.emb.accessrequests.request.dto.RequestDtos.SubmitRequestBody;
import org.emb.accessrequests.request.service.RequestService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Role checks for these routes are in SecurityConfig; visibility checks are in RequestService. */
@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping("/mine")
    public List<AccessRequestDto> mine(@AuthenticationPrincipal AppUserDetails principal) {
        return requestService.listMine(principal.user());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccessRequestDto submit(@AuthenticationPrincipal AppUserDetails principal,
            @RequestBody SubmitRequestBody body) {
        return requestService.submit(principal.user(), body.accessTypeId(), body.reason());
    }

    @PostMapping("/{id}/decision")
    public AccessRequestDto decide(@AuthenticationPrincipal AppUserDetails principal,
            @PathVariable long id, @RequestBody DecisionBody body) {
        return requestService.decide(principal.user(), id, body.decision(), body.comment());
    }
}
