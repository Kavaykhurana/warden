export type Role = "STUDENT" | "FACULTY" | "HOD" | "ADMIN";
export interface UserSummary { id: string; fullName: string; role: Role; }
export interface User { id: string; email: string; fullName: string; role: Role; departmentCode: string | null; departmentName: string | null; rollNo: string | null; mentorName: string | null; }
export interface AuthResponse { accessToken: string; expiresIn: number; user: User; }
export type ErrorCode = "VALIDATION_FAILED" | "UNAUTHORIZED" | "FORBIDDEN" | "NOT_FOUND" | "CONFLICT" | "BUSINESS_RULE" | "RATE_LIMITED" | "AI_UNAVAILABLE" | "INTERNAL";
export interface ApiError { status: number; code: ErrorCode; message: string; fieldErrors?: Record<string, string>; }
export type Decision = "ALLOWED" | "DENIED" | "QUEUED" | "FAILED";
export interface ToolEvent { tool: string; arguments: Record<string, string>; decision: Decision; message: string; actionRequestId: string | null; }
export interface Conversation { id: string; title: string; createdAt: string; updatedAt: string; }
export interface Message { id: number; role: "USER" | "ASSISTANT"; content: string; toolEvents: ToolEvent[]; flagged: boolean; createdAt: string; }
export interface ChatTurn { userMessage: Message; assistantMessage: Message; }
export type ActionStatus = "PENDING" | "EXECUTED" | "FAILED" | "REJECTED" | "EXPIRED";
export type ActionTool = "requestGradeChange" | "deactivateUser";
export interface ActionRequest { id: string; toolName: ActionTool; summary: string; arguments: Record<string, string>; requiredRole: "HOD" | "ADMIN"; departmentCode: string | null; status: ActionStatus; requestedBy: UserSummary; reviewer: UserSummary | null; reviewNote: string | null; failureReason: string | null; createdAt: string; expiresAt: string; decidedAt: string | null; canReview: boolean; }
export type LeaveStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";
export interface DutyLeave { id: number; student: { id: string; fullName: string; rollNo: string }; startDate: string; endDate: string; workingDays: number; eventName: string; reason: string; reasonFlagged: boolean; status: LeaveStatus; requiresHod: boolean; decidedBy: UserSummary | null; decisionNote: string | null; decidedAt: string | null; createdAt: string; canDecide: boolean; canCancel: boolean; }
export type Grade = "A+" | "A" | "B+" | "B" | "C+" | "C" | "D" | "F";
export interface AttendanceRow { courseCode: string; courseTitle: string; classesHeld: number; classesAttended: number; dutyLeaveCredited: number; percentage: number; shortage: boolean; classesNeededFor75: number; }
export interface GradeRow { courseCode: string; courseTitle: string; credits: number; grade: Grade | null; gradePoint: number | null; }
export interface AcademicSummary { semester: string; attendance: AttendanceRow[]; grades: GradeRow[]; sgpa: number | null; creditsEarned: number; }
export interface Course { code: string; title: string; departmentCode: string; credits: number; semester: string; faculty: UserSummary; classesHeld: number; enrolledCount: number; shortageCount: number; }
export interface RosterRow { enrollmentId: number; studentId: string; fullName: string; rollNo: string; classesAttended: number; dutyLeaveCredited: number; percentage: number; shortage: boolean; grade: Grade | null; pendingGradeChange: boolean; }
export interface AdminUser extends User { active: boolean; createdAt: string; }
export type AuditEventType = "LOGIN_SUCCESS" | "LOGIN_FAILURE" | "TOKEN_REUSE_DETECTED" | "TOOL_ALLOWED" | "TOOL_DENIED" | "TOOL_QUEUED" | "TOOL_FAILED" | "ACTION_EXECUTED" | "ACTION_FAILED" | "ACTION_REJECTED" | "ACTION_EXPIRED" | "DUTY_LEAVE_DECIDED" | "INJECTION_FLAGGED" | "INJECTION_BLOCKED" | "RATE_LIMITED";
export interface AuditEvent { id: number; actor: UserSummary | null; eventType: AuditEventType; toolName: string | null; details: Record<string, string>; prevHash: string; hash: string; createdAt: string; }
export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; }
export interface ChainVerification { intact: boolean; verifiedCount: number; brokenAtId: number | null; verifiedAt: string; }
