import type * as T from "./types";
export interface Api {
  auth: { login(email: string, password: string): Promise<T.AuthResponse>; refresh(): Promise<T.AuthResponse>; logout(): Promise<void>; me(): Promise<T.User>; };
  conversations: { list(): Promise<T.Conversation[]>; create(title?: string): Promise<T.Conversation>; rename(id: string, title: string): Promise<T.Conversation>; remove(id: string): Promise<void>; messages(id: string): Promise<T.Message[]>; send(id: string, content: string): Promise<T.ChatTurn>; };
  academics: { mine(): Promise<T.AcademicSummary> };
  courses: { list(): Promise<T.Course[]>; roster(code: string): Promise<T.RosterRow[]>; requestGradeChange(code: string, body: { enrollmentId: number; newGrade: T.Grade; reason: string }): Promise<T.ActionRequest>; };
  dutyLeave: { mine(): Promise<T.DutyLeave[]>; apply(body: { startDate: string; endDate: string; eventName: string; reason: string }): Promise<T.DutyLeave>; cancel(id: number): Promise<T.DutyLeave>; review(status?: "PENDING" | "ALL"): Promise<T.DutyLeave[]>; decide(id: number, body: { approve: boolean; note: string }): Promise<T.DutyLeave>; };
  approvals: { list(scope: "review" | "mine", status?: "PENDING" | "ALL"): Promise<T.ActionRequest[]>; approve(id: string, note?: string): Promise<T.ActionRequest>; reject(id: string, note: string): Promise<T.ActionRequest>; };
  admin: { users(): Promise<T.AdminUser[]>; audit(q: { eventType?: T.AuditEventType; actorId?: string; from?: string; page?: number; size?: number }): Promise<T.Page<T.AuditEvent>>; verify(): Promise<T.ChainVerification>; };
}
