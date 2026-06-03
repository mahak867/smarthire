// ── SmartHire · frontend/src/pages/Users.tsx ──
import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { usersApi } from "../utils/api";
import toast from "react-hot-toast";
import { Search, Lock, Unlock, UserCheck, UserX, ChevronDown } from "lucide-react";

const ROLES = ["ADMIN", "RECRUITER", "CANDIDATE"];
const ROLE_COLORS: Record<string, string> = {
  ADMIN:     "bg-purple-900/50 text-purple-300 border border-purple-800",
  RECRUITER: "bg-indigo-900/50 text-indigo-300 border border-indigo-800",
  CANDIDATE: "bg-gray-700 text-gray-300",
};

export default function Users() {
  const queryClient = useQueryClient();
  const [search,  setSearch]  = useState("");
  const [role,    setRole]    = useState("");
  const [page,    setPage]    = useState(0);
  const [editing, setEditing] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ["users", { search, role, page }],
    queryFn: () => usersApi.list({ search: search || undefined, role: role || undefined, page, size: 20 }),
    keepPreviousData: true,
  });

  const roleMutation = useMutation({
    mutationFn: ({ id, role }: { id: string; role: string }) => usersApi.updateRole(id, role),
    onSuccess: () => { toast.success("Role updated"); queryClient.invalidateQueries({ queryKey: ["users"] }); setEditing(null); },
    onError: (e: any) => toast.error(e?.response?.data?.error ?? "Failed to update role"),
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => usersApi.setActive(id, active),
    onSuccess: (_, { active }) => { toast.success(active ? "Account activated" : "Account deactivated"); queryClient.invalidateQueries({ queryKey: ["users"] }); },
  });

  const unlockMutation = useMutation({
    mutationFn: (id: string) => usersApi.unlock(id),
    onSuccess: () => { toast.success("Account unlocked"); queryClient.invalidateQueries({ queryKey: ["users"] }); },
  });

  const users      = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-white">User Management</h1>
          <p className="text-sm text-gray-500 mt-0.5">{data?.totalElements ?? 0} users</p>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-[#111827] border border-[#1F2937] rounded-xl p-4 flex gap-3 flex-wrap">
        <div className="relative flex-1 min-w-[200px]">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" />
          <input value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
            placeholder="Search by name or email…"
            className="w-full pl-8 pr-3 py-2 bg-[#0B0F1A] border border-[#374151] rounded-lg text-sm text-white placeholder-gray-600 focus:outline-none focus:border-indigo-500" />
        </div>
        <select value={role} onChange={e => { setRole(e.target.value); setPage(0); }}
          className="px-3 py-2 bg-[#0B0F1A] border border-[#374151] rounded-lg text-sm text-gray-300 focus:outline-none focus:border-indigo-500">
          <option value="">All Roles</option>
          {ROLES.map(r => <option key={r}>{r}</option>)}
        </select>
      </div>

      {/* Table */}
      <div className="bg-[#111827] border border-[#1F2937] rounded-xl overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center py-20">
            <div className="animate-spin rounded-full h-6 w-6 border-2 border-indigo-500 border-t-transparent" />
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-[#1F2937]">
                <th className="text-left px-5 py-3 text-xs text-gray-500 font-medium">User</th>
                <th className="text-left px-5 py-3 text-xs text-gray-500 font-medium">Role</th>
                <th className="text-left px-5 py-3 text-xs text-gray-500 font-medium">Status</th>
                <th className="text-left px-5 py-3 text-xs text-gray-500 font-medium">Joined</th>
                <th className="text-left px-5 py-3 text-xs text-gray-500 font-medium">Last Login</th>
                <th className="px-5 py-3 text-xs text-gray-500 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#1F2937]">
              {users.map((u: any) => (
                <tr key={u.id} className="hover:bg-[#1F2937]/40 transition-colors">
                  <td className="px-5 py-4">
                    <p className="font-medium text-white">{u.firstName} {u.lastName}</p>
                    <p className="text-xs text-gray-500">{u.email}</p>
                  </td>

                  {/* Role — inline editable */}
                  <td className="px-5 py-4">
                    {editing === u.id ? (
                      <select autoFocus defaultValue={u.role}
                        onBlur={e => {
                          if (e.target.value !== u.role) roleMutation.mutate({ id: u.id, role: e.target.value });
                          else setEditing(null);
                        }}
                        className="bg-[#0B0F1A] border border-indigo-500 rounded px-2 py-1 text-xs text-white focus:outline-none">
                        {ROLES.map(r => <option key={r}>{r}</option>)}
                      </select>
                    ) : (
                      <button onClick={() => setEditing(u.id)}
                        className={`flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium ${ROLE_COLORS[u.role] ?? "bg-gray-700 text-gray-400"} hover:opacity-80 transition-opacity`}>
                        {u.role} <ChevronDown size={10} />
                      </button>
                    )}
                  </td>

                  <td className="px-5 py-4">
                    <div className="flex items-center gap-1.5">
                      <span className={`w-1.5 h-1.5 rounded-full ${u.active ? "bg-emerald-400" : "bg-gray-600"}`} />
                      <span className="text-xs text-gray-400">
                        {u.accountLocked ? "Locked" : u.active ? "Active" : "Inactive"}
                      </span>
                      {u.failedLoginAttempts > 0 && (
                        <span className="text-xs text-yellow-500 ml-1">({u.failedLoginAttempts} fails)</span>
                      )}
                    </div>
                  </td>

                  <td className="px-5 py-4 text-xs text-gray-500">
                    {new Date(u.createdAt).toLocaleDateString("en-IN")}
                  </td>

                  <td className="px-5 py-4 text-xs text-gray-500">
                    {u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleDateString("en-IN") : "Never"}
                  </td>

                  <td className="px-5 py-4">
                    <div className="flex items-center gap-1.5 justify-end">
                      {u.accountLocked && (
                        <button onClick={() => unlockMutation.mutate(u.id)} title="Unlock account"
                          className="p-1.5 hover:bg-yellow-900/40 text-yellow-400 rounded transition-colors">
                          <Unlock size={13} />
                        </button>
                      )}
                      <button onClick={() => statusMutation.mutate({ id: u.id, active: !u.active })}
                        title={u.active ? "Deactivate" : "Activate"}
                        className={`p-1.5 rounded transition-colors ${u.active
                          ? "hover:bg-red-900/40 text-red-400"
                          : "hover:bg-emerald-900/40 text-emerald-400"}`}>
                        {u.active ? <UserX size={13} /> : <UserCheck size={13} />}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {totalPages > 1 && (
        <div className="flex justify-center gap-2">
          <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
            className="px-3 py-1.5 bg-[#1F2937] hover:bg-[#374151] text-gray-300 text-sm rounded-lg disabled:opacity-40 transition-colors">
            ← Previous
          </button>
          <span className="px-3 py-1.5 text-gray-500 text-sm">Page {page + 1} of {totalPages}</span>
          <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
            className="px-3 py-1.5 bg-[#1F2937] hover:bg-[#374151] text-gray-300 text-sm rounded-lg disabled:opacity-40 transition-colors">
            Next →
          </button>
        </div>
      )}
    </div>
  );
}
