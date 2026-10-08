package edu.ashbrook.warden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultMatcher;

import edu.ashbrook.warden.support.IntegrationTest;
import edu.ashbrook.warden.support.StubChatModel;
import tools.jackson.databind.JsonNode;

/**
 * Serialises real responses from every endpoint and checks field names and JSON types against the TypeScript
 * contract in {@code frontend/src/api/types.ts}. A renamed, missing or extra field fails here before the UI breaks.
 */
class ContractIT extends IntegrationTest {

    /** Field → type: string, number, boolean, object, array, ts (ISO timestamp, +05:30), date (YYYY-MM-DD); "?" = nullable. */
    private static Map<String, String> shape(String... pairs) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }

    private static final Map<String, String> USER_SUMMARY = shape("id", "string", "fullName", "string", "role", "string");
    private static final Map<String, String> USER = shape("id", "string", "email", "string", "fullName", "string",
            "role", "string", "departmentCode", "string?", "departmentName", "string?", "rollNo", "string?",
            "mentorName", "string?");
    private static final Map<String, String> ADMIN_USER = with(USER, "active", "boolean", "createdAt", "ts");
    private static final Map<String, String> AUTH = shape("accessToken", "string", "expiresIn", "number", "user", "object");
    private static final Map<String, String> CONVERSATION = shape("id", "string", "title", "string", "createdAt", "ts",
            "updatedAt", "ts");
    private static final Map<String, String> MESSAGE = shape("id", "number", "role", "string", "content", "string",
            "toolEvents", "array", "flagged", "boolean", "createdAt", "ts");
    private static final Map<String, String> TOOL_EVENT = shape("tool", "string", "arguments", "object",
            "decision", "string", "message", "string", "actionRequestId", "string?");
    private static final Map<String, String> ACTION_REQUEST = shape("id", "string", "toolName", "string",
            "summary", "string", "arguments", "object", "requiredRole", "string", "departmentCode", "string?",
            "status", "string", "requestedBy", "object", "reviewer", "object?", "reviewNote", "string?",
            "failureReason", "string?", "createdAt", "ts", "expiresAt", "ts", "decidedAt", "ts?", "canReview", "boolean");
    private static final Map<String, String> DUTY_LEAVE = shape("id", "number", "student", "object", "startDate", "date",
            "endDate", "date", "workingDays", "number", "eventName", "string", "reason", "string",
            "reasonFlagged", "boolean", "status", "string", "requiresHod", "boolean", "decidedBy", "object?",
            "decisionNote", "string?", "decidedAt", "ts?", "createdAt", "ts", "canDecide", "boolean",
            "canCancel", "boolean");
    private static final Map<String, String> STUDENT_REF = shape("id", "string", "fullName", "string", "rollNo", "string");
    private static final Map<String, String> ATTENDANCE_ROW = shape("courseCode", "string", "courseTitle", "string",
            "classesHeld", "number", "classesAttended", "number", "dutyLeaveCredited", "number", "percentage", "number",
            "shortage", "boolean", "classesNeededFor75", "number");
    private static final Map<String, String> GRADE_ROW = shape("courseCode", "string", "courseTitle", "string",
            "credits", "number", "grade", "string?", "gradePoint", "number?");
    private static final Map<String, String> ACADEMIC_SUMMARY = shape("semester", "string", "attendance", "array",
            "grades", "array", "sgpa", "number?", "creditsEarned", "number");
    private static final Map<String, String> COURSE = shape("code", "string", "title", "string",
            "departmentCode", "string", "credits", "number", "semester", "string", "faculty", "object",
            "classesHeld", "number", "enrolledCount", "number", "shortageCount", "number");
    private static final Map<String, String> ROSTER_ROW = shape("enrollmentId", "number", "studentId", "string",
            "fullName", "string", "rollNo", "string", "classesAttended", "number", "dutyLeaveCredited", "number",
            "percentage", "number", "shortage", "boolean", "grade", "string?", "pendingGradeChange", "boolean");
    private static final Map<String, String> AUDIT_EVENT = shape("id", "number", "actor", "object?",
            "eventType", "string", "toolName", "string?", "details", "object", "prevHash", "string", "hash", "string",
            "createdAt", "ts");
    private static final Map<String, String> PAGE = shape("content", "array", "page", "number", "size", "number",
            "totalElements", "number", "totalPages", "number");
    private static final Map<String, String> CHAIN = shape("intact", "boolean", "verifiedCount", "number",
            "brokenAtId", "number?", "verifiedAt", "ts");
    private static final Map<String, String> API_ERROR = shape("status", "number", "code", "string", "message", "string");

    private static Map<String, String> with(Map<String, String> base, String... more) {
        Map<String, String> m = new LinkedHashMap<>(base);
        m.putAll(shape(more));
        return m;
    }

    private JsonNode call(RequestBuilder request, ResultMatcher expected) throws Exception {
        return json.readTree(mvc.perform(request).andExpect(expected).andReturn().getResponse().getContentAsString());
    }

    private static void assertShape(JsonNode node, Map<String, String> spec, String where) {
        assertThat(node.isObject()).as(where + " is an object").isTrue();
        Set<String> actual = new HashSet<>(node.propertyNames());
        assertThat(actual).as(where + " fields").isEqualTo(spec.keySet());
        spec.forEach((field, type) -> {
            JsonNode value = node.get(field);
            boolean nullable = type.endsWith("?");
            String base = nullable ? type.substring(0, type.length() - 1) : type;
            if (value.isNull()) {
                assertThat(nullable).as(where + "." + field + " may not be null").isTrue();
                return;
            }
            String label = where + "." + field;
            switch (base) {
                case "string" -> assertThat(value.isString()).as(label + " is a string").isTrue();
                case "number" -> assertThat(value.isNumber()).as(label + " is a number").isTrue();
                case "boolean" -> assertThat(value.isBoolean()).as(label + " is a boolean").isTrue();
                case "object" -> assertThat(value.isObject()).as(label + " is an object").isTrue();
                case "array" -> assertThat(value.isArray()).as(label + " is an array").isTrue();
                case "ts" -> assertThat(value.asString()).as(label)
                        .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+05:30");
                case "date" -> assertThat(value.asString()).as(label).matches("\\d{4}-\\d{2}-\\d{2}");
                default -> throw new IllegalArgumentException(type);
            }
        });
    }

    private static void assertEach(JsonNode array, Map<String, String> spec, String where) {
        assertThat(array.isArray()).as(where + " is an array").isTrue();
        assertThat(array.size()).as(where + " is not empty").isPositive();
        array.forEach(item -> assertShape(item, spec, where + "[]"));
    }

    private static void assertStringMap(JsonNode node, String where) {
        node.properties().forEach(e -> assertThat(e.getValue().isString()).as(where + "." + e.getKey()).isTrue());
    }

    private void assertActionRequest(JsonNode r, String where) {
        assertShape(r, ACTION_REQUEST, where);
        assertShape(r.get("requestedBy"), USER_SUMMARY, where + ".requestedBy");
        if (!r.get("reviewer").isNull()) {
            assertShape(r.get("reviewer"), USER_SUMMARY, where + ".reviewer");
        }
        assertStringMap(r.get("arguments"), where + ".arguments");
    }

    private void assertDutyLeave(JsonNode l, String where) {
        assertShape(l, DUTY_LEAVE, where);
        assertShape(l.get("student"), STUDENT_REF, where + ".student");
        if (!l.get("decidedBy").isNull()) {
            assertShape(l.get("decidedBy"), USER_SUMMARY, where + ".decidedBy");
        }
    }

    @Test
    void authAndUserEndpoints() throws Exception {
        JsonNode auth = call(post("/api/auth/login").with(fromIp("10.1.0.1")).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", AARAV, "password", PASSWORD))), status().isOk());
        assertShape(auth, AUTH, "AuthResponse");
        assertShape(auth.get("user"), USER, "AuthResponse.user");
        assertShape(call(get("/api/me").with(as(PRIYA)), status().isOk()), USER, "User(admin)");
        JsonNode admins = call(get("/api/admin/users").with(as(PRIYA)), status().isOk());
        assertEach(admins, ADMIN_USER, "AdminUser");
    }

    @Test
    void conversationAndChatEndpoints() throws Exception {
        JsonNode created = call(post("/api/conversations").with(as(MEERA)).contentType(MediaType.APPLICATION_JSON)
                .content("{}"), status().isCreated());
        assertShape(created, CONVERSATION, "Conversation");
        assertThat(created.get("title").asString()).isEqualTo("New conversation");
        String id = created.get("id").asString();

        model.respond(p -> StubChatModel.text("Here is your roster."));
        JsonNode turn = call(post("/api/conversations/" + id + "/messages").with(as(MEERA))
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("content", "Show my courses"))),
                status().isOk());
        assertThat(turn.propertyNames()).containsExactlyInAnyOrder("userMessage", "assistantMessage");
        assertShape(turn.get("userMessage"), MESSAGE, "ChatTurn.userMessage");
        assertShape(turn.get("assistantMessage"), MESSAGE, "ChatTurn.assistantMessage");

        model.respond(p -> p.getInstructions().stream().anyMatch(m -> m.getMessageType().getValue().equals("tool"))
                ? StubChatModel.text("Done.") : StubChatModel.toolCall("listMyCourses", "{}"));
        JsonNode withTool = call(post("/api/conversations/" + id + "/messages").with(as(MEERA))
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("content", "List my courses"))),
                status().isOk());
        assertEach(withTool.get("assistantMessage").get("toolEvents"), TOOL_EVENT, "ToolEvent");
        assertStringMap(withTool.get("assistantMessage").get("toolEvents").get(0).get("arguments"), "ToolEvent.arguments");

        assertEach(call(get("/api/conversations").with(as(MEERA)), status().isOk()), CONVERSATION, "Conversation[]");
        assertEach(call(get("/api/conversations/" + id + "/messages").with(as(MEERA)), status().isOk()), MESSAGE,
                "Message[]");
        assertShape(call(patch("/api/conversations/" + id).with(as(MEERA)).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("title", "Roster questions"))), status().isOk()), CONVERSATION, "Conversation(renamed)");
        mvc.perform(delete("/api/conversations/" + id).with(as(MEERA))).andExpect(status().isNoContent());
    }

    @Test
    void academicsAndCourseEndpoints() throws Exception {
        JsonNode summary = call(get("/api/academics/me").with(as(AARAV)), status().isOk());
        assertShape(summary, ACADEMIC_SUMMARY, "AcademicSummary");
        assertEach(summary.get("attendance"), ATTENDANCE_ROW, "AttendanceRow");
        assertEach(summary.get("grades"), GRADE_ROW, "GradeRow");

        JsonNode courses = call(get("/api/courses").with(as(PRIYA)), status().isOk());
        assertEach(courses, COURSE, "Course");
        courses.forEach(c -> assertShape(c.get("faculty"), USER_SUMMARY, "Course.faculty"));
        assertEach(call(get("/api/courses/UCS301/roster").with(as(MEERA)), status().isOk()), ROSTER_ROW, "RosterRow");

        long enrollment = jdbc.queryForObject("select e.id from enrollments e join users u on u.id = e.student_id "
                + "where u.email = ? and e.course_code = 'UCS301'", Long.class, ISHA);
        assertActionRequest(call(post("/api/courses/UCS301/grade-changes").with(as(MEERA))
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("enrollmentId", enrollment,
                        "newGrade", "A", "reason", "Moderation board corrected the total marks."))),
                status().isCreated()), "ActionRequest(created)");
    }

    @Test
    void dutyLeaveEndpoints() throws Exception {
        assertEach(call(get("/api/duty-leave/mine").with(as(KABIR)), status().isOk()), DUTY_LEAVE, "DutyLeave[]");
        call(get("/api/duty-leave/mine").with(as(KABIR)), status().isOk())
                .forEach(l -> assertDutyLeave(l, "DutyLeave(mine)"));
        JsonNode applied = call(post("/api/duty-leave").with(as(ANANYA)).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("startDate", "2026-10-26", "endDate", "2026-10-27", "eventName", "Robotics expo",
                        "reason", "Presenting our line-follower robot at the state robotics expo."))), status().isCreated());
        assertDutyLeave(applied, "DutyLeave(applied)");
        assertDutyLeave(call(post("/api/duty-leave/" + applied.get("id").asLong() + "/cancel").with(as(ANANYA)),
                status().isOk()), "DutyLeave(cancelled)");
        JsonNode review = call(get("/api/duty-leave/review?status=ALL").with(as(SUNITA)), status().isOk());
        assertThat(review.size()).isPositive();
        review.forEach(l -> assertDutyLeave(l, "DutyLeave(review)"));
        assertDutyLeave(call(post("/api/duty-leave/1/decide").with(as(MEERA)).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("approve", true, "note", ""))), status().isOk()), "DutyLeave(decided)");
    }

    @Test
    void approvalEndpoints() throws Exception {
        JsonNode mine = call(get("/api/approvals?scope=mine&status=ALL").with(as(PRIYA)), status().isOk());
        assertThat(mine.size()).isPositive();
        mine.forEach(r -> assertActionRequest(r, "ActionRequest(mine)"));
        JsonNode review = call(get("/api/approvals?scope=review&status=ALL").with(as(SUNITA)), status().isOk());
        review.forEach(r -> assertActionRequest(r, "ActionRequest(review)"));
        String pending = review.valueStream().filter(r -> r.get("canReview").asBoolean()).findFirst().orElseThrow()
                .get("id").asString();
        assertActionRequest(call(post("/api/approvals/" + pending + "/reject").with(as(SUNITA))
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("note", "Please attach the marks sheet."))),
                status().isOk()), "ActionRequest(rejected)");
        String rohan = jdbc.queryForObject("select id::text from action_requests where tool_name = 'deactivateUser' "
                + "and status = 'PENDING'", String.class);
        assertActionRequest(call(post("/api/approvals/" + rohan + "/approve").with(as(VIKRAM)), status().isOk()),
                "ActionRequest(approved)");
    }

    @Test
    void auditEndpoints() throws Exception {
        JsonNode page = call(get("/api/admin/audit?size=100").with(as(PRIYA)), status().isOk());
        assertShape(page, PAGE, "Page<AuditEvent>");
        assertEach(page.get("content"), AUDIT_EVENT, "AuditEvent");
        page.get("content").forEach(e -> {
            assertStringMap(e.get("details"), "AuditEvent.details");
            assertThat(e.get("hash").asString()).matches("[0-9a-f]{64}");
            if (!e.get("actor").isNull()) {
                assertShape(e.get("actor"), USER_SUMMARY, "AuditEvent.actor");
            }
        });
        assertShape(call(get("/api/admin/audit/verify").with(as(PRIYA)), status().isOk()), CHAIN, "ChainVerification");
    }

    @Test
    void errorsAlwaysUseTheApiErrorShape() throws Exception {
        assertShape(call(get("/api/me"), status().isUnauthorized()), API_ERROR, "ApiError(401)");
        assertShape(call(get("/api/admin/users").with(as(AARAV)), status().isForbidden()), API_ERROR, "ApiError(403)");
        assertShape(call(get("/api/courses/UCS303/roster").with(as(MEERA)), status().isForbidden()), API_ERROR,
                "ApiError(403 method)");
        assertShape(call(get("/api/conversations/00000000-0000-0000-0000-000000000000/messages").with(as(MEERA)),
                status().isNotFound()), API_ERROR, "ApiError(404)");
        JsonNode invalid = call(post("/api/duty-leave").with(as(AARAV)).contentType(MediaType.APPLICATION_JSON)
                .content("{}"), status().isBadRequest());
        assertShape(invalid, with(API_ERROR, "fieldErrors", "object"), "ApiError(400)");
        assertStringMap(invalid.get("fieldErrors"), "ApiError.fieldErrors");
        assertShape(call(post("/api/duty-leave").with(as(AARAV)).contentType(MediaType.APPLICATION_JSON)
                .content("{not json"), status().isBadRequest()), API_ERROR, "ApiError(400 unreadable)");
        JsonNode wrongType = call(post("/api/auth/login").with(fromIp("10.1.0.9")).contentType(MediaType.TEXT_PLAIN)
                .content("email=a"), status().isBadRequest());
        assertShape(wrongType, API_ERROR, "ApiError(400 wrong content type)");
        assertThat(wrongType.get("message").asString()).isEqualTo("Send the request body as JSON.");
    }
}
