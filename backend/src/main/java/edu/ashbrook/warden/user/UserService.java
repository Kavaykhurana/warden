package edu.ashbrook.warden.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.ashbrook.warden.approval.ActionRequest;
import edu.ashbrook.warden.approval.ActionRequestDto;
import edu.ashbrook.warden.approval.ActionRequestRepository;
import edu.ashbrook.warden.approval.ActionTool;
import edu.ashbrook.warden.approval.ApprovalEligibility;
import edu.ashbrook.warden.common.BusinessRuleException;
import edu.ashbrook.warden.common.ConflictException;
import edu.ashbrook.warden.common.FieldValidationException;
import edu.ashbrook.warden.common.NotFoundException;
import edu.ashbrook.warden.common.Text;

@Service
public class UserService {

    private final UserRepository users;
    private final ActionRequestRepository actionRequests;
    private final ApprovalEligibility eligibility;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final Duration approvalTtl;

    public UserService(UserRepository users, ActionRequestRepository actionRequests, ApprovalEligibility eligibility,
                       CurrentUser currentUser, Clock clock, @Value("${warden.approvals.ttl}") Duration approvalTtl) {
        this.users = users;
        this.actionRequests = actionRequests;
        this.eligibility = eligibility;
        this.currentUser = currentUser;
        this.clock = clock;
        this.approvalTtl = approvalTtl;
    }

    @Transactional(readOnly = true)
    public UserDto me() {
        return UserDto.of(currentUser.user());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AdminUserDto> adminUsers() {
        return users.findAllWithMentor().stream()
                .sorted(Comparator.comparing((User u) -> u.getRole().ordinal()).reversed()
                        .thenComparing(User::getFullName))
                .map(AdminUserDto::of)
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ActionRequestDto requestDeactivation(String email, String reason) {
        return requestDeactivation(email, reason, null);
    }

    /** Always queued: another ADMIN must approve before anything changes. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ActionRequestDto requestDeactivation(String email, String reason, UUID conversationId) {
        String cleanReason = Text.trim(reason);
        if (!Text.lengthBetween(cleanReason, 10, 500)) {
            throw new FieldValidationException("reason", "Give a reason of 10 to 500 characters.");
        }
        User requester = currentUser.user();
        User target = users.findByEmailIgnoreCase(Text.trim(email).toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("No account uses that email."));
        if (target.getId().equals(requester.getId())) {
            throw new BusinessRuleException("You can't deactivate your own account.");
        }
        if (!target.isActive()) {
            throw new BusinessRuleException("That account is already inactive.");
        }
        if (actionRequests.existsPendingDeactivation(target.getId().toString())) {
            throw new ConflictException("A deactivation for this account is already pending.");
        }
        Map<String, String> args = new LinkedHashMap<>();
        args.put("targetUserId", target.getId().toString());
        args.put("email", target.getEmail());
        args.put("fullName", target.getFullName());
        args.put("role", target.getRole().name());
        args.put("reason", cleanReason);
        ApprovalEligibility.Routing routing = eligibility.routeDeactivation();
        Instant now = clock.instant();
        ActionRequest request = new ActionRequest(conversationId, requester, ActionTool.deactivateUser, args,
                "Deactivate %s (%s)".formatted(target.getFullName(), target.getEmail()), routing.requiredRole(),
                routing.departmentCode(), now, now.plus(approvalTtl));
        try {
            actionRequests.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A deactivation for this account is already pending.");
        }
        return ActionRequestDto.of(request, false);
    }

    @Transactional(readOnly = true)
    public Optional<String> activeHodName(String departmentCode) {
        return users.findActiveHod(departmentCode).map(User::getFullName);
    }
}
