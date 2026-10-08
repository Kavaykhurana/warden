package edu.ashbrook.warden.dutyleave;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import edu.ashbrook.warden.config.ClockConfig;
import edu.ashbrook.warden.user.User;
import edu.ashbrook.warden.user.UserSummary;

/** Contract type {@code DutyLeave}. {@code reason} is untrusted text written by the student. */
public record DutyLeaveDto(long id, StudentRef student, LocalDate startDate, LocalDate endDate, int workingDays,
                           String eventName, String reason, boolean reasonFlagged, LeaveStatus status,
                           boolean requiresHod, UserSummary decidedBy, String decisionNote, OffsetDateTime decidedAt,
                           OffsetDateTime createdAt, boolean canDecide, boolean canCancel) {

    public record StudentRef(String id, String fullName, String rollNo) {
    }

    static DutyLeaveDto of(DutyLeaveRequest l, boolean canDecide, boolean canCancel) {
        User s = l.getStudent();
        return new DutyLeaveDto(l.getId(), new StudentRef(s.getId().toString(), s.getFullName(), s.getRollNo()),
                l.getStartDate(), l.getEndDate(), l.getWorkingDays(), l.getEventName(), l.getReason(),
                l.isReasonFlagged(), l.getStatus(), l.isRequiresHod(), UserSummary.of(l.getDecidedBy()),
                l.getDecisionNote(), ClockConfig.ist(l.getDecidedAt()), ClockConfig.ist(l.getCreatedAt()), canDecide,
                canCancel);
    }
}
