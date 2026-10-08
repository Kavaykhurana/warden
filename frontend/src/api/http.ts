import type * as T from "./types";
import type { Api } from "./api";
let accessToken: string | null = null;
let refreshPromise: Promise<T.AuthResponse> | null = null;
export function setAccessToken(token: string | null): void { accessToken = token; }
async function request<R>(path: string, init: RequestInit = {}, timeout = 15000, retried = false): Promise<R> {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeout);
  try {
    const response = await fetch(`/api${path}`, { ...init, signal: controller.signal, credentials: "include", headers: { Accept: "application/json", ...(init.body ? { "Content-Type": "application/json" } : {}), ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}), ...init.headers } });
    if (response.status === 204) return undefined as R;
    if (response.status === 401 && !path.startsWith("/auth/") && !retried) {
      refreshPromise ??= request<T.AuthResponse>("/auth/refresh", { method: "POST" }).finally(() => { refreshPromise = null; });
      try { const auth = await refreshPromise; accessToken = auth.accessToken; return await request<R>(path, init, timeout, true); }
      catch { accessToken = null; window.location.assign(`/login?next=${encodeURIComponent(window.location.pathname)}`); throw { status: 401, code: "UNAUTHORIZED", message: "Your session ended. Sign in again." } satisfies T.ApiError; }
    }
    if (!response.ok) { try { throw await response.json() as T.ApiError; } catch (e) { if (typeof e === "object" && e !== null && "code" in e) throw e; throw { status: response.status, code: "INTERNAL", message: "Something went wrong on our side. Try again." } satisfies T.ApiError; } }
    return await response.json() as R;
  } catch (error) {
    if (typeof error === "object" && error !== null && "code" in error) throw error;
    if (error instanceof DOMException && error.name === "AbortError") throw { status: 0, code: "INTERNAL", message: "The request took too long. Try again." } satisfies T.ApiError;
    if (error instanceof TypeError) throw { status: 0, code: "INTERNAL", message: "Can't reach the server. Check your connection and try again." } satisfies T.ApiError;
    throw error;
  } finally { window.clearTimeout(timer); }
}
const json = (method: string, data?: unknown): RequestInit => ({ method, ...(data === undefined ? {} : { body: JSON.stringify(data) }) });
export const httpApi: Api = {
  auth: { login: (email, password) => request("/auth/login", json("POST", { email, password })), refresh: () => request("/auth/refresh", json("POST")), logout: () => request("/auth/logout", json("POST")), me: () => request("/me") },
  conversations: { list: () => request("/conversations"), create: (title) => request("/conversations", json("POST", { title })), rename: (id, title) => request(`/conversations/${id}`, json("PATCH", { title })), remove: (id) => request(`/conversations/${id}`, json("DELETE")), messages: (id) => request(`/conversations/${id}/messages`), send: (id, content) => request(`/conversations/${id}/messages`, json("POST", { content }), 60000) },
  academics: { mine: () => request("/academics/me") },
  courses: { list: () => request("/courses"), roster: (code) => request(`/courses/${code}/roster`), requestGradeChange: (code, body) => request(`/courses/${code}/grade-changes`, json("POST", body)) },
  dutyLeave: { mine: () => request("/duty-leave/mine"), apply: (body) => request("/duty-leave", json("POST", body)), cancel: (id) => request(`/duty-leave/${id}/cancel`, json("POST")), review: (status = "PENDING") => request(`/duty-leave/review?status=${status}`), decide: (id, body) => request(`/duty-leave/${id}/decide`, json("POST", body)) },
  approvals: { list: (scope, status = "PENDING") => request(`/approvals?scope=${scope}&status=${status}`), approve: (id, note) => request(`/approvals/${id}/approve`, json("POST", { note })), reject: (id, note) => request(`/approvals/${id}/reject`, json("POST", { note })) },
  admin: { users: () => request("/admin/users"), audit: (q) => request(`/admin/audit?${new URLSearchParams(Object.entries(q).filter(([, v]) => v !== undefined).map(([k, v]) => [k, String(v)]))}`), verify: () => request("/admin/audit/verify") }
};
