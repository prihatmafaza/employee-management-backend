package org.emb.accessrequests.request.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.emb.accessrequests.accesstype.dto.AccessTypeDto;
import org.emb.accessrequests.request.entity.AccessRequest;
import org.emb.accessrequests.request.entity.Approval;
import org.emb.accessrequests.request.enums.ApprovalStep;
import org.emb.accessrequests.request.enums.Decision;
import org.emb.accessrequests.request.enums.RequestStatus;
import org.emb.accessrequests.request.service.RequestService;
import org.emb.accessrequests.user.entity.User;

/** JSON shapes for requests, as defined in the API contract. */
public final class RequestDtos {

    private RequestDtos() {
    }

    /** Body of {@code POST /api/requests}. Fields are checked in {@link RequestService}. */
    public record SubmitRequestBody(Long accessTypeId, String reason) {
    }

    /** Body of {@code POST /api/requests/{id}/decision}. Fields are checked in {@link RequestService}. */
    public record DecisionBody(String decision, String comment) {
    }

    public record PersonRef(long id, String fullName) {

        public static PersonRef from(User user) {
            return new PersonRef(user.getId(), user.getFullName());
        }
    }

    public record ApprovalDto(
            ApprovalStep step,
            Decision decision,
            PersonRef approver,
            String comment,
            Instant decidedAt) {

        public static ApprovalDto from(Approval approval) {
            return new ApprovalDto(approval.getStep(), approval.getDecision(),
                    PersonRef.from(approval.getApprover()), approval.getComment(), approval.getDecidedAt());
        }
    }

    public record AccessRequestDto(
            long id,
            AccessTypeDto accessType,
            PersonRef requester,
            String reason,
            RequestStatus status,
            ApprovalStep currentStep,
            List<ApprovalDto> approvals,
            Instant createdAt,
            Instant updatedAt) {

        public static AccessRequestDto from(AccessRequest request) {
            return new AccessRequestDto(
                    request.getId(),
                    AccessTypeDto.from(request.getAccessType()),
                    PersonRef.from(request.getUser()),
                    request.getReason(),
                    request.getStatus(),
                    request.getCurrentStep(),
                    request.getApprovals().stream().map(ApprovalDto::from).toList(),
                    request.getCreatedAt(),
                    request.getUpdatedAt());
        }
    }

    /** An {@link AccessRequestDto} with one more field, serialized flat as in the contract. */
    public record ReviewItemDto(@JsonUnwrapped AccessRequestDto request, boolean canAct) {
    }
}
