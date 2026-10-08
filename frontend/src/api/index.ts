import type { Api } from "./api";
import { httpApi } from "./http";
import { mockApi } from "./mock/mockApi";
export const api: Api = import.meta.env.VITE_USE_MOCK === "false" ? httpApi : mockApi;
