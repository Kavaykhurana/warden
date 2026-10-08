import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "./AuthProvider";
export function RequireAuth({ children }: { children: React.ReactNode }) { const { user, loading } = useAuth(); const location = useLocation(); if (loading) return <div className="bootstrap"><img src="/scenes/pixel-flower.svg" width="64" height="64" alt="" /><p className="t-caption">Opening Warden…</p></div>; return user ? <>{children}</> : <Navigate to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`} replace />; }
