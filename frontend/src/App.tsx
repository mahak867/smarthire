// ── SmartHire · frontend/src/App.tsx ──
import { Suspense, lazy } from "react";
import { BrowserRouter, Routes, Route, Navigate, NavLink, useNavigate } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "react-hot-toast";
import { useAuthStore } from "./store/authStore";
import {
  LayoutDashboard, Briefcase, FileText, User, LogOut, PlusCircle,
  Users as UsersIcon, Bell
} from "lucide-react";
import { useUserNotifications } from "./hooks/useAiScoring";

// ── Lazy-loaded pages ─────────────────────────────────────────────────────────
const Login             = lazy(() => import("./pages/Login"));
const Register          = lazy(() => import("./pages/Register"));
const Dashboard         = lazy(() => import("./pages/Dashboard"));
const Jobs              = lazy(() => import("./pages/Jobs"));
const JobDetail         = lazy(() => import("./pages/JobDetail"));
const NewJob            = lazy(() => import("./pages/NewJob"));
const ApplicationDetail = lazy(() => import("./pages/ApplicationDetail"));
const ApplicationsList  = lazy(() => import("./pages/ApplicationsList"));
const Profile           = lazy(() => import("./pages/Profile"));
const Users             = lazy(() => import("./pages/Users"));

// ── React Query client ────────────────────────────────────────────────────────
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
  },
});

// ── Suspense fallback ─────────────────────────────────────────────────────────
const PageLoader = () => (
  <div className="flex items-center justify-center h-64">
    <div className="animate-spin rounded-full h-8 w-8 border-2 border-indigo-500 border-t-transparent" />
  </div>
);

// ── Auth guards ───────────────────────────────────────────────────────────────
function RequireAuth({ children }: { children: React.ReactNode }) {
  const isAuth = useAuthStore((s) => s.isAuthenticated);
  return isAuth ? <>{children}</> : <Navigate to="/login" replace />;
}

function RequireGuest({ children }: { children: React.ReactNode }) {
  const isAuth = useAuthStore((s) => s.isAuthenticated);
  return !isAuth ? <>{children}</> : <Navigate to="/dashboard" replace />;
}

// ── App shell ─────────────────────────────────────────────────────────────────
function AppLayout({ children }: { children: React.ReactNode }) {
  const { user, clearAuth } = useAuthStore();
  const { notifications, clearNotifications } = useUserNotifications();
  const navigate   = useNavigate();
  const isRecruiter = user?.role === "RECRUITER" || user?.role === "ADMIN";

  const handleLogout = () => {
    clearAuth();
    navigate("/login");
  };

  const navCls = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm transition-colors ${
      isActive
        ? "bg-indigo-600/20 text-indigo-400 font-medium border-l-2 border-indigo-500 pl-[10px]"
        : "text-gray-400 hover:bg-[#1F2937] hover:text-white"
    }`;

  return (
    <div className="flex min-h-screen bg-[#0B0F1A]">
      {/* Sidebar */}
      <aside className="w-56 shrink-0 bg-[#0F1522] border-r border-[#1F2937] flex flex-col">
        {/* Logo */}
        <div className="px-4 py-5 border-b border-[#1F2937]">
          <div className="text-indigo-400 font-bold text-lg tracking-tight">SmartHire</div>
          <div className="text-xs text-gray-500 mt-0.5">Recruitment Intelligence</div>
        </div>

        {/* Nav */}
        <nav className="flex-1 px-3 py-4 space-y-0.5">
          {isRecruiter && (
            <NavLink to="/dashboard" className={navCls}>
              <LayoutDashboard size={15} /> Dashboard
            </NavLink>
          )}
          <NavLink to="/jobs" className={navCls}>
            <Briefcase size={15} /> Jobs
          </NavLink>
          {isRecruiter && (
            <NavLink to="/jobs/new" className={navCls}>
              <PlusCircle size={15} /> Post a Job
            </NavLink>
          )}
          <NavLink to="/applications" className={navCls}>
            <FileText size={15} /> Applications
          </NavLink>
          <NavLink to="/profile" className={navCls}>
            <User size={15} /> Profile
          </NavLink>
          {user?.role === "ADMIN" && (
            <NavLink to="/admin/users" className={navCls}>
              <UsersIcon size={15} /> Users
            </NavLink>
          )}
        </nav>

        {/* User footer */}
        <div className="px-3 py-4 border-t border-[#1F2937]">
          <div className="px-3 py-2 mb-1">
            <div className="text-xs font-medium text-white truncate">
              {user?.firstName} {user?.lastName}
            </div>
            <div className="text-xs text-gray-500 truncate">{user?.email}</div>
            <div className="text-xs text-indigo-400 mt-0.5 uppercase tracking-wide" style={{ fontSize: 10 }}>
              {user?.role}
            </div>
          </div>
          {notifications.length > 0 && (
            <div className="px-3 py-2 mb-1">
              <div className="flex items-center justify-between">
                <span className="flex items-center gap-1.5 text-xs text-yellow-400">
                  <Bell size={12} /> {notifications.length} new
                </span>
                <button onClick={clearNotifications} className="text-xs text-gray-600 hover:text-gray-400">Clear</button>
              </div>
              <div className="mt-1 space-y-1 max-h-24 overflow-y-auto">
                {notifications.slice(0,3).map((n, i) => (
                  <p key={i} className="text-xs text-gray-400 truncate">{n.message}</p>
                ))}
              </div>
            </div>
          )}
          <button onClick={handleLogout}
            className="flex items-center gap-2 w-full px-3 py-2 text-sm text-gray-400 hover:text-white hover:bg-[#1F2937] rounded-lg transition-colors">
            <LogOut size={14} /> Sign out
          </button>
        </div>
      </aside>

      {/* Main */}
      <main className="flex-1 overflow-auto">
        <div className="max-w-6xl mx-auto px-6 py-6">
          <Suspense fallback={<PageLoader />}>
            {children}
          </Suspense>
        </div>
      </main>
    </div>
  );
}

// ── Wrapped route helper ──────────────────────────────────────────────────────
function Protected({ children }: { children: React.ReactNode }) {
  return (
    <RequireAuth>
      <AppLayout>{children}</AppLayout>
    </RequireAuth>
  );
}

// ── Root ──────────────────────────────────────────────────────────────────────
export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Suspense fallback={null}>
          <Routes>
            {/* Public */}
            <Route path="/login"    element={<RequireGuest><Login /></RequireGuest>} />
            <Route path="/register" element={<RequireGuest><Register /></RequireGuest>} />

            {/* Protected */}
            <Route path="/"                     element={<Protected><Navigate to="/dashboard" replace /></Protected>} />
            <Route path="/dashboard"            element={<Protected><Dashboard /></Protected>} />

            {/* Jobs */}
            <Route path="/jobs"                 element={<Protected><Jobs /></Protected>} />
            <Route path="/jobs/new"             element={<Protected><NewJob /></Protected>} />
            <Route path="/jobs/:id"             element={<Protected><JobDetail /></Protected>} />
            <Route path="/jobs/:id/edit"        element={<Protected><NewJob /></Protected>} />

            {/* Applications */}
            <Route path="/applications"         element={<Protected><ApplicationsList /></Protected>} />
            <Route path="/applications/:id"     element={<Protected><ApplicationDetail /></Protected>} />

            {/* Admin */}
            <Route path="/admin/users"          element={<Protected><Users /></Protected>} />

            {/* Profile */}
            <Route path="/profile"              element={<Protected><Profile /></Protected>} />

            <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        </Suspense>
      </BrowserRouter>
      <Toaster
        position="bottom-right"
        toastOptions={{
          style: { background: "#1F2937", color: "#F9FAFB", border: "1px solid #374151", fontSize: 13 },
          success: { iconTheme: { primary: "#10B981", secondary: "#fff" } },
          error:   { iconTheme: { primary: "#EF4444", secondary: "#fff" } },
        }}
      />
    </QueryClientProvider>
  );
}
