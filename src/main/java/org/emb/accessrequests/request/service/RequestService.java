package org.emb.accessrequests.request.service;

import java.util.List;

import org.emb.accessrequests.auth.dto.SessionUser;
import org.emb.accessrequests.request.dto.RequestDtos.AccessRequestDto;
import org.emb.accessrequests.request.dto.RequestDtos.ReviewItemDto;

/**
 * The request workflow. Every check from the contract happens here, in the
 * contract's order, including visibility: the URL rules in SecurityConfig only
 * gate endpoints by role.
 */
public interface RequestService {

    int MAX_TEXT_LENGTH = 500;

    List<AccessRequestDto> listMine(SessionUser me);

    AccessRequestDto submit(SessionUser me, Long accessTypeId, String rawReason);

    List<ReviewItemDto> listReviewItems(SessionUser me);

    /**
     * Records the caller's decision and advances the request. Two reviewers
     * deciding at once can't both succeed: the loser gets 409.
     */
    AccessRequestDto decide(SessionUser me, long requestId, String rawDecision, String rawComment);
}
