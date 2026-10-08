import type { Role } from "../api/types";
import { Link } from "react-router-dom";
export function RequireRole({ roles, role, children }: { roles: Role[]; role: Role; children: React.ReactNode }) { return roles.includes(role) ? <>{children}</> : <section className="forbidden"><img src="/scenes/pixel-flower.svg" width="56" height="56" alt="" /><h2 className="t-heading-sm">You don't have access to this</h2><p className="t-body">This page is available to a different role.</p><Link className="link-arrow" to="/overview">Back to overview <span aria-hidden="true">→</span></Link></section>; }
