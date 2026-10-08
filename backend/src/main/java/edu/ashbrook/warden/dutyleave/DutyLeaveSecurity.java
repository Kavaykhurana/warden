package edu.ashbrook.warden.dutyleave;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.ashbrook.warden.user.CurrentUser;
import edu.ashbrook.warden.user.User;
import edu.ashbrook.warden.user.UserRepository;

/** Duty-leave checks referenced from {@code @PreAuthorize} and reused for the DTO's {@code canDecide}. */
@Component("dutyLeaveSecurity")
public class DutyLeaveSecurity {

    private final DutyLeaveRepository leaves;
    private final UserRepository users;

    public DutyLeaveSecurity(DutyLeaveRepository leaves, UserRepository users) {
        this.leaves = leaves;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public boolean owns(Long id, Authentication authentication) {
        return leaves.findDetailed(id)
                .map(l -> l.getStudent().getId().equals(CurrentUser.idOf(authentication)))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canDecide(Long id, Authentication authentication) {
        User caller = users.findById(CurrentUser.idOf(authentication)).orElse(null);
        return leaves.findDetailed(id).map(l -> canDecide(l, caller)).orElse(false);
    }

    /**
     * Never the student themselves. The HOD of the student's department may always decide; the student's mentor
     * only when the request is three working days or fewer.
     */
    public boolean canDecide(DutyLeaveRequest leave, User caller) {
        if (caller == null || !caller.isActive()) {
            return false;
        }
        User student = leave.getStudent();
        if (caller.getId().equals(student.getId())) {
            return false;
        }
        if (caller.isHodOf(student.getDepartmentCode())) {
            return true;
        }
        return student.getMentor() != null && student.getMentor().getId().equals(caller.getId()) && !leave.isRequiresHod();
    }
}
