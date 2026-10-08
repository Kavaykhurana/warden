package edu.ashbrook.warden.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import edu.ashbrook.warden.common.ConflictException;
import edu.ashbrook.warden.support.IntegrationTest;
import edu.ashbrook.warden.support.TestInfrastructure;
import edu.ashbrook.warden.user.DepartmentRepository;
import edu.ashbrook.warden.user.Role;
import edu.ashbrook.warden.user.User;
import edu.ashbrook.warden.user.UserService;
import edu.ashbrook.warden.auth.ActiveUserFilter;

class ApprovalIT extends IntegrationTest {

    @Autowired
    private ApprovalService approvals;
    @Autowired
    private ApprovalExpiryJob expiryJob;
    @Autowired
    private DepartmentRepository departments;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserService userService;
    @Autowired
    private ActiveUserFilter activeUserFilter;

    private long enrollment(String studentEmail, String course) {
        Long id = jdbc.queryForObject("select e.id from enrollments e join users u on u.id = e.student_id "
                + "where u.email = ? and e.course_code = ?", Long.class, studentEmail, course);
        return id == null ? -1 : id;
    }

    /** Raises a grade change through the REST API and returns the new request's id. */
    private String requestChange(String requester, String course, String student, String newGrade) throws Exception {
        String response = mvc.perform(post("/api/courses/" + course + "/grade-changes").with(as(requester))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("enrollmentId", enrollment(student, course), "newGrade", newGrade,
                                "reason", "Moderation board corrected the total marks."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asString();
    }

    private String requestDeactivation(String requester, String target) {
        return runAs(requester, () -> userService.requestDeactivation(target, "Account needs to be closed for this test.").id());
    }

    private String seededPending(String tool) {
        return jdbc.queryForObject("select id::text from action_requests where tool_name = ? and status = 'PENDING' "
                + "order by created_at limit 1", String.class, tool);
    }

    @Test
    void theRequesterCanNeverReviewTheirOwnRequest() throws Exception {
        String rohan = seededPending("deactivateUser");
        mvc.perform(post("/api/approvals/" + rohan + "/approve").with(as(PRIYA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You're not allowed to review this request."));
        String byId = "$[?(@.id == '" + rohan + "')]";
        mvc.perform(get("/api/approvals").with(as(PRIYA)))
                .andExpect(jsonPath(byId).isEmpty());
        mvc.perform(get("/api/approvals?scope=mine").with(as(PRIYA)))
                .andExpect(jsonPath(byId + ".canReview").value(false));
        mvc.perform(get("/api/approvals").with(as(VIKRAM)))
                .andExpect(jsonPath(byId + ".canReview").value(true));
        assertThatThrownBy(() -> jdbc.update("update action_requests set reviewer_id = requested_by where id = ?::uuid",
                rohan)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aHodOfAnotherDepartmentCannotReview() throws Exception {
        String id = requestChange(MEERA, "UCS301", ISHA, "A");
        mvc.perform(post("/api/approvals/" + id + "/approve").with(as(RAKESH))).andExpect(status().isForbidden());
        mvc.perform(post("/api/approvals/" + id + "/reject").with(as(RAKESH)).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("note", "Not my department.")))).andExpect(status().isForbidden());
    }

    @Test
    void anAdminCannotApproveAGradeChangeRoutedToTheHod() throws Exception {
        String id = requestChange(MEERA, "UCS310", ISHA, "A+");
        mvc.perform(post("/api/approvals/" + id + "/approve").with(as(PRIYA))).andExpect(status().isForbidden());
        mvc.perform(post("/api/approvals/" + id + "/approve").with(as(MEERA))).andExpect(status().isForbidden());
        mvc.perform(post("/api/approvals/" + id + "/reject").with(as(SUNITA)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("note", "no"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.note").exists());
        mvc.perform(post("/api/approvals/" + id + "/reject").with(as(SUNITA)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("note", "Marks were already moderated."))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void aGradeChangeRaisedByTheHodRoutesToAnAdmin() throws Exception {
        String response = mvc.perform(post("/api/courses/UCS420/grade-changes").with(as(SUNITA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("enrollmentId", enrollment(AARAV, "UCS420"), "newGrade", "A+",
                                "reason", "Project viva marks were missing from the total."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiredRole").value("ADMIN"))
                .andExpect(jsonPath("$.departmentCode").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(response).get("id").asString();
        mvc.perform(post("/api/approvals/" + id + "/approve").with(as(SUNITA))).andExpect(status().isForbidden());
        mvc.perform(post("/api/approvals/" + id + "/approve").with(as(VIKRAM)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("note", "Checked with the exam cell."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.reviewer.fullName").value("Vikram Sethi"));
        assertThat(jdbc.queryForObject("select grade from enrollments where id = ?", String.class,
                enrollment(AARAV, "UCS420"))).isEqualTo("A+");
        assertThat(jdbc.queryForObject("select count(*) from grade_changes where action_request_id = ?::uuid",
                Long.class, id)).isEqualTo(1);
    }

    @Test
    void onlyTheTeachingFacultyCanRequestAndOnePendingPerStudent() throws Exception {
        mvc.perform(post("/api/courses/UCS303/grade-changes").with(as(MEERA)).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("enrollmentId", enrollment(ISHA, "UCS303"), "newGrade", "A",
                        "reason", "Moderation board corrected the total marks.")))).andExpect(status().isForbidden());
        mvc.perform(post("/api/courses/UCS310/grade-changes").with(as(MEERA)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("enrollmentId", enrollment(ANANYA, "UCS310"), "newGrade", "B+",
                                "reason", "Moderation board corrected the total marks."))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));
        mvc.perform(post("/api/courses/UCS310/grade-changes").with(as(MEERA)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("enrollmentId", enrollment(ANANYA, "UCS310"), "newGrade", "A",
                                "reason", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
        requestChange(MEERA, "UCS310", ANANYA, "A");
        mvc.perform(post("/api/courses/UCS310/grade-changes").with(as(MEERA)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("enrollmentId", enrollment(ANANYA, "UCS310"), "newGrade", "A+",
                                "reason", "Moderation board corrected the total marks."))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A change for this student is already pending."));
        mvc.perform(post("/api/courses/UCS310/grade-changes").with(as(PRIYA)).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("enrollmentId", enrollment(ISHA, "UCS310"), "newGrade", "B",
                        "reason", "Moderation board corrected the total marks.")))).andExpect(status().isForbidden());
    }

    @Test
    void executionRevalidatesTheGradeAndFailsIfItChanged() throws Exception {
        String id = requestChange(MEERA, "UCS301", AARAV, "A");
        jdbc.update("update enrollments set grade = 'B' where id = ?", enrollment(AARAV, "UCS301"));
        mvc.perform(post("/api/approvals/" + id + "/approve").with(as(SUNITA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("The grade changed after this request was raised."));
        assertThat(jdbc.queryForObject("select grade from enrollments where id = ?", String.class,
                enrollment(AARAV, "UCS301"))).isEqualTo("B");
        assertThat(jdbc.queryForObject("select count(*) from audit_events where event_type = 'ACTION_FAILED' "
                + "and details->>'actionRequestId' = ?", Long.class, id)).isEqualTo(1);
    }

    @Test
    void deactivationFailsWhileTheTargetTeachesOrMentors() throws Exception {
        String arjun = requestDeactivation(PRIYA, ARJUN);
        mvc.perform(post("/api/approvals/" + arjun + "/approve").with(as(VIKRAM)))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Reassign their courses first."));

        User mentor = users.save(new User("neha.faculty@ashbrook.edu", passwordEncoder.encode(PASSWORD),
                "Dr. Neha Joshi", Role.FACULTY, departments.findById("ECE").orElseThrow(), null, null,
                TestInfrastructure.NOW));
        users.save(new User("tara.student@ashbrook.edu", passwordEncoder.encode(PASSWORD), "Tara Das",
                Role.STUDENT, departments.findById("ECE").orElseThrow(), "102306099", mentor, TestInfrastructure.NOW));
        String neha = requestDeactivation(PRIYA, "neha.faculty@ashbrook.edu");
        mvc.perform(post("/api/approvals/" + neha + "/approve").with(as(VIKRAM)))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Reassign their mentees first."));
    }

    @Test
    void theLastActiveAdminCannotBeDeactivated() throws Exception {
        String deactivatePriya = requestDeactivation(VIKRAM, PRIYA);
        String deactivateVikram = requestDeactivation(PRIYA, VIKRAM);
        try {
            mvc.perform(post("/api/approvals/" + deactivateVikram + "/approve").with(as(VIKRAM)))
                    .andExpect(jsonPath("$.status").value("EXECUTED"));
            mvc.perform(post("/api/approvals/" + deactivatePriya + "/approve").with(as(PRIYA)))
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.failureReason").value("They're the last active administrator."));
            assertThat(users.findByEmailIgnoreCase(PRIYA).orElseThrow().isActive()).isTrue();
        } finally {
            // Other tests in this class still sign in as Vikram.
            jdbc.update("update users set active = true where email = ?", VIKRAM);
            activeUserFilter.evict(id(VIKRAM));
        }
    }

    @Test
    void expiredRequestsAreMarkedAndCanNoLongerBeDecided() throws Exception {
        String id = requestChange(MEERA, "UCS310", AARAV, "A");
        jdbc.update("update action_requests set expires_at = ?::timestamptz where id = ?::uuid",
                TestInfrastructure.NOW.minus(Duration.ofMinutes(1)).toString(), id);
        mvc.perform(post("/api/approvals/" + id + "/approve").with(as(SUNITA))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This request has expired."));

        long before = auditCount("ACTION_EXPIRED");
        assertThat(expiryJob.expireDue()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select status from action_requests where id = ?::uuid", String.class, id))
                .isEqualTo("EXPIRED");
        assertThat(auditCount("ACTION_EXPIRED")).isGreaterThan(before);
        mvc.perform(post("/api/approvals/" + id + "/approve").with(as(SUNITA))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This request was already decided."));
    }

    @Test
    void twoSimultaneousApprovalsExecuteExactlyOnce() throws Exception {
        UUID id = UUID.fromString(seededPending("requestGradeChange"));
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> approve = () -> {
            start.await();
            try {
                return runAs(SUNITA, () -> approvals.approve(id, null).status().name());
            } catch (ConflictException e) {
                return "CONFLICT";
            }
        };
        List<String> outcomes = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<String> first = pool.submit(approve);
            Future<String> second = pool.submit(approve);
            start.countDown();
            outcomes.add(first.get());
            outcomes.add(second.get());
        }
        assertThat(outcomes).containsExactlyInAnyOrder("EXECUTED", "CONFLICT");
        assertThat(jdbc.queryForObject("select count(*) from grade_changes where action_request_id = ?", Long.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select grade from enrollments where id = ?", String.class,
                enrollment(KABIR, "UCS310"))).isEqualTo("A");
    }
}
