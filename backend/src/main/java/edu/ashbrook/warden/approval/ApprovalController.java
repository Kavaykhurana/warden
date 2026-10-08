package edu.ashbrook.warden.approval;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.ashbrook.warden.common.FieldValidationException;
import edu.ashbrook.warden.common.StatusFilter;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    public record NoteRequest(String note) {
    }

    private final ApprovalService approvals;

    public ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    @GetMapping
    List<ActionRequestDto> list(@RequestParam(defaultValue = "review") String scope,
                                @RequestParam(defaultValue = "PENDING") String status) {
        ApprovalService.Scope parsedScope = switch (scope) {
            case "review" -> ApprovalService.Scope.REVIEW;
            case "mine" -> ApprovalService.Scope.MINE;
            default -> throw new FieldValidationException("scope", "Use review or mine.");
        };
        return approvals.list(parsedScope, StatusFilter.pendingOnly(status));
    }

    @PostMapping("/{id}/approve")
    ActionRequestDto approve(@PathVariable UUID id, @RequestBody(required = false) NoteRequest body) {
        return approvals.approve(id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/reject")
    ActionRequestDto reject(@PathVariable UUID id, @RequestBody(required = false) NoteRequest body) {
        return approvals.reject(id, body == null ? null : body.note());
    }
}
