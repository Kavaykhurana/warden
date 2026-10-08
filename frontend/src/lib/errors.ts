import type { ApiError } from "../api/types";
export function isApiError(value: unknown): value is ApiError { return typeof value === "object" && value !== null && "status" in value && "code" in value && "message" in value; }
export function apiMessage(value: unknown): string { return isApiError(value) ? value.message : "Something went wrong on our side. Try again."; }
