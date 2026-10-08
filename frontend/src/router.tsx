import { createBrowserRouter, Navigate } from "react-router-dom";
import { RequireAuth } from "./auth/RequireAuth";
import { AppFrame, LoginPage, OverviewPage, AssistantPage, CoursesPage, CourseRosterPage, DutyLeavePage, ApprovalsPage, AuditPage, UsersPage, NotFoundPage } from "./pages/App";
function protectedPage(element: React.ReactNode) { return <RequireAuth><AppFrame>{element}</AppFrame></RequireAuth>; }
export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/", element: <Navigate to="/overview" replace /> },
  { path: "/overview", element: protectedPage(<OverviewPage />) },
  { path: "/assistant", element: protectedPage(<AssistantPage />) },
  { path: "/assistant/:conversationId", element: protectedPage(<AssistantPage />) },
  { path: "/courses", element: protectedPage(<CoursesPage />) },
  { path: "/courses/:code", element: protectedPage(<CourseRosterPage />) },
  { path: "/duty-leave", element: protectedPage(<DutyLeavePage />) },
  { path: "/approvals", element: protectedPage(<ApprovalsPage />) },
  { path: "/admin/audit", element: protectedPage(<AuditPage />) },
  { path: "/admin/users", element: protectedPage(<UsersPage />) },
  { path: "*", element: protectedPage(<NotFoundPage />) }
]);
