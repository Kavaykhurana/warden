package edu.ashbrook.warden.dutyleave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import edu.ashbrook.warden.support.IntegrationTest;

class DutyLeaveIT extends IntegrationTest {

    private ResultActions apply(String email, String start, String end) throws Exception {
        return mvc.perform(post("/api/duty-leave").with(as(email)).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("startDate", start, "endDate", end, "eventName", "Inter-college quiz",
                        "reason", "Representing the department at the inter-college quiz finals."))));
    }

    private ResultActions decide(String email, long id, boolean approve, String note) throws Exception {
        return mvc.perform(post("/api/duty-leave/" + id + "/decide").with(as(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(note == null ? Map.of("approve", approve) : Map.of("approve", approve, "note", note))));
    }

    private long leaveId(String email) {
        Long id = jdbc.queryForObject("select d.id from duty_leave_requests d join users u on u.id = d.student_id "
                + "where u.email = ? and d.status = 'PENDING' order by d.id limit 1", Long.class, email);
        return id == null ? -1 : id;
    }

    @Test
    void overlappingAPendingOrApprovedRequestIsAConflict() throws Exception {
        apply(ANANYA, "2026-10-14", "2026-10-15").andExpect(status().isCreated());
        apply(KABIR, "2026-09-22", "2026-09-23").andExpect(status().isConflict());
        apply(ANANYA, "2026-10-15", "2026-10-16")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("This overlaps a duty leave request you already have."));
    }

    @Test
    void dateWindowIsThirtyDaysBackAndSixtyAhead() throws Exception {
        apply(ANANYA, "2026-09-07", "2026-09-07").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));
        apply(ANANYA, "2026-12-08", "2026-12-08").andExpect(status().isUnprocessableContent());
        apply(ANANYA, "2026-09-08", "2026-09-08").andExpect(status().isCreated())
                .andExpect(jsonPath("$.workingDays").value(1))
                .andExpect(jsonPath("$.requiresHod").value(false))
                .andExpect(jsonPath("$.canCancel").value(true));
    }

    @Test
    void workingDaysMustBeOneToTen() throws Exception {
        apply(ANANYA, "2026-10-10", "2026-10-11").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Those dates don't include a working day."));
        apply(ANANYA, "2026-10-26", "2026-11-09").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Duty leave can cover at most 10 working days."));
        apply(ANANYA, "2026-10-26", "2026-11-06").andExpect(status().isCreated())
                .andExpect(jsonPath("$.workingDays").value(10))
                .andExpect(jsonPath("$.requiresHod").value(true));
    }

    @Test
    void fieldLengthsAreValidated() throws Exception {
        mvc.perform(post("/api/duty-leave").with(as(ISHA)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("startDate", "2026-11-02", "endDate", "2026-10-30", "eventName", "ab",
                                "reason", "too short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.eventName").exists())
                .andExpect(jsonPath("$.fieldErrors.reason").exists())
                .andExpect(jsonPath("$.fieldErrors.endDate").exists());
    }

    @Test
    void onlyStudentsApply() throws Exception {
        apply(MEERA, "2026-10-26", "2026-10-27").andExpect(status().isForbidden());
    }

    @Test
    void approvalCreditsEachCourseButNeverMoreThanTheClassesMissed() throws Exception {
        long isha = leaveId(ISHA);
        decide(SUNITA, isha, true, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy.fullName").value("Dr. Sunita Rao"));
        // Isha missed 2, 7, 2 and 3 classes; the leave is 5 working days.
        assertThat(jdbc.queryForList("select e.course_code || ':' || e.duty_leave_credited from enrollments e "
                + "join users u on u.id = e.student_id where u.email = ? order by e.course_code", String.class, ISHA))
                .containsExactly("UCS301:2", "UCS303:5", "UCS310:2", "UCS420:3");
        assertThat(jdbc.queryForObject("select count(*) from audit_events where event_type = 'DUTY_LEAVE_DECIDED' "
                + "and details->>'leaveId' = ?", Long.class, String.valueOf(isha))).isEqualTo(1);

        decide(SUNITA, isha, true, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This request was already decided."));
    }

    @Test
    void mentorDecidesShortLeaveOnlyAndHodOfAnotherDepartmentNever() throws Exception {
        long aarav = leaveId(AARAV);
        decide(RAKESH, aarav, true, null).andExpect(status().isForbidden());
        decide(ARJUN, aarav, true, null).andExpect(status().isForbidden());
        decide(PRIYA, aarav, true, null).andExpect(status().isForbidden());
        decide(MEERA, aarav, false, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.note").exists());
        decide(MEERA, aarav, false, "Please attach the invitation letter.").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mvc.perform(post("/api/duty-leave").with(as(ISHA)).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("startDate", "2026-11-09", "endDate", "2026-11-13", "eventName", "Hackathon finals",
                        "reason", "Our team is in the national hackathon finals that week."))))
                .andExpect(status().isCreated());
        Long longLeave = jdbc.queryForObject("select max(id) from duty_leave_requests", Long.class);
        decide(MEERA, longLeave, true, null).andExpect(status().isForbidden());
    }

    @Test
    void reviewQueueIsScopedToMenteesOrDepartment() throws Exception {
        mvc.perform(get("/api/duty-leave/review?status=ALL").with(as(ARJUN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$[*].student.fullName", everyItem(is("Kabir Singh"))));
        mvc.perform(get("/api/duty-leave/review?status=ALL").with(as(RAKESH)))
                .andExpect(jsonPath("$[*].student.rollNo", everyItem(startsWith("102306"))));
        mvc.perform(get("/api/duty-leave/review?status=ALL").with(as(SUNITA)))
                .andExpect(jsonPath("$[*].student.rollNo", everyItem(startsWith("102303"))));
        mvc.perform(get("/api/duty-leave/review").with(as(AARAV))).andExpect(status().isForbidden());
        mvc.perform(get("/api/duty-leave/review?status=SOME").with(as(MEERA))).andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheOwnerCancelsAndOnlyWhilePending() throws Exception {
        apply(KABIR, "2026-10-26", "2026-10-27").andExpect(status().isCreated());
        long id = leaveId(KABIR);
        mvc.perform(post("/api/duty-leave/" + id + "/cancel").with(as(ISHA))).andExpect(status().isForbidden());
        mvc.perform(post("/api/duty-leave/" + id + "/cancel").with(as(ARJUN))).andExpect(status().isForbidden());
        mvc.perform(post("/api/duty-leave/" + id + "/cancel").with(as(KABIR))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.canCancel").value(false));
        mvc.perform(post("/api/duty-leave/" + id + "/cancel").with(as(KABIR))).andExpect(status().isConflict());
        // A cancelled request no longer blocks the same dates.
        apply(KABIR, "2026-10-26", "2026-10-27").andExpect(status().isCreated());
    }

    @Test
    void selfDecisionIsImpossibleInCodeAndInTheDatabase() throws Exception {
        long kabirsOwn = jdbc.queryForObject("select d.id from duty_leave_requests d join users u on u.id = d.student_id "
                + "where u.email = ? order by d.id limit 1", Long.class, KABIR);
        decide(KABIR, kabirsOwn, true, null).andExpect(status().isForbidden());
        assertThatThrownBy(() -> jdbc.update("update duty_leave_requests set decided_by = student_id where id = ?",
                kabirsOwn)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void injectedReasonIsStoredFlaggedButStillAccepted() throws Exception {
        mvc.perform(get("/api/duty-leave/review?status=ALL").with(as(MEERA)))
                .andExpect(jsonPath("$[?(@.id == 1)].reasonFlagged").value(true))
                .andExpect(jsonPath("$[?(@.id == 2)].reasonFlagged").value(false));
    }
}
