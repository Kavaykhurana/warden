import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { AuthResponse, User } from "../api/types";
import { api } from "../api";
import { setAccessToken } from "../api/http";
type AuthValue = { user: User | null; loading: boolean; login: (email: string, password: string) => Promise<void>; logout: () => Promise<void> };
const AuthContext = createContext<AuthValue | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => { let active = true; api.auth.refresh().then((result: AuthResponse) => { if (!active) return; setAccessToken(result.accessToken); setUser(result.user); }).catch(() => { if (active) { setAccessToken(null); setUser(null); } }).finally(() => { if (active) setLoading(false); }); return () => { active = false; }; }, []);
  const value = useMemo<AuthValue>(() => ({ user, loading, login: async (email, password) => { const result = await api.auth.login(email, password); setAccessToken(result.accessToken); setUser(result.user); }, logout: async () => { await api.auth.logout(); setAccessToken(null); setUser(null); } }), [user, loading]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth(): AuthValue { const value = useContext(AuthContext); if (!value) throw new Error("AuthProvider is missing."); return value; }
