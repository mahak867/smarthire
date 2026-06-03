// ── SmartHire · frontend/src/pages/ApplicationsList.tsx ──
import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { applicationsApi, jobsApi } from "../utils/api";
import toast from "react-hot-toast";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "../store/authStore";
import { Search, Filter, ChevronRight, Clock, TrendingUp, Download } from "lucide-react";
import { exportApi } from "../utils/api";

const STATUS_OPTIONS = ["", "APPLIED", "SCREENING", "SHORTLISTED", "INTERVIEW", "OFFERED", "REJECTED"];
const STATUS_COLORS: Record<string, string> = {
  APPLIED:     "bg-blue-900/50 text-blue-300",
  SCREENING:   "bg-yellow-900/50 text-yellow-300",
  SHORTLISTED: "bg-indigo-900/50 text-indigo-300",
  INTERVIEW:   "bg-purple-900/50 text-purple-300",
  OFFERED:     "bg-emerald-900/50 text-emerald-300",
  REJECTED:    "bg-red-900/50 text-red-300",
  WITHDRAWN:   "bg-gray-700 text-gray-400",
};

const RECOMMENDATION_COLORS: Record<string, string> = {
  STRONG_YES: "text-emerald-400",
  YES:        "text-green-400",
  MAYBE:      "text-yellow-400",
  NO:         "text-red-400",
};

function ScoreBar({ score }: { score: number | null }) {
  if (score == null) return <span className="text-xs text-gray-600 italic">Scoring…</span>;
  const color = score >= 70 ? "bg-emerald-500" : score >= 50 ? "bg-yellow-500" : "bg-red-500";
  return (
    <div className="flex items-center gap-2">
      <div className="flex-1 h-1.5 bg-[#1F2937] rounded-full overflow-hidden">
        <div className={`h-full rounded-full ${color}`} style={{ width: `${score}%` }} />
      </div>
      <span className="text-xs text-gray-300 w-8 text-right">{score.toFixed(0)}</span>
    </div>
  );
}


function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a   = document.createElement("a");
  a.href = url; a.download = filename; a.click();
  URL.revokeObjectURL(url);
}

export default function ApplicationsList() {
  const { user } = useAuthStore();
  const isCandidate = user?.role === "CANDIDATE";

  const [jobId,    setJobId]    = useState("");
  const [status,   setStatus]   = useState("");
  const [minScore, setMinScore] = useState("");
  const [search,   setSearch]   = useState("");
  const [page,     setPage]     = useState(0);

  // Recruiter: all applications (filterable). Candidate: own applications.
  const bulkMutation = useMutation({
    mutationFn: ({ ids, status }: { ids: string[]; status: string }) =>
      applicationsApi.bulkStatus(ids, status),
    onSuccess: (res: any) => {
      toast.success(res.message);
      setSelectedIds(new Set());
      setBulkStatus("");
      queryClient.invalidateQueries({ queryKey: ["applications"] });
    },
    onError: (e: any) => toast.error(e?.response?.data?.error ?? "Bulk update failed"),
  });

  const { data, isLoading } = useQuery({
    queryKey: ["applications", { jobId, status, minScore, page, isCandidate }],
    queryFn: () => isCandidate
      ? applicationsApi.my({ page, size: 20 })
      : applicationsApi.list({ jobId: jobId || undefined, status: status || undefined,
                               minScore: minScore ? Number(minScore) : undefined, page, size: 20 }),
    keepPreviousData: true,
  });

  // Recruiter: job list for filter dropdown
  const { data: jobsData } = useQuery({
    queryKey: ["jobs-brief"],
    queryFn: () => jobsApi.list({ status: "OPEN", size: 100 }),
    enabled: !isCandidate,
  });

  const applications = data?.content ?? [];
  const total        = data?.totalElements ?? 0;
  const totalPages   = data?.totalPages ?? 0;

  const filtered = search
    ? applications.filter((a: any) =>
        a.candidateName?.toLowerCase().includes(search.toLowerCase()) ||
        a.jobTitle?.toLowerCase().includes(search.toLowerCase()))
    : applications;

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-white">
            {isCandidate ? "My Applications" : "Applications"}
          </h1>
          <p className="text-sm text-gray-500 mt-0.5">{total} total</p>
        </div>
        {!isCandidate && (
          <div className="flex gap-2">
            <button
              onClick={async () => {
                const r = await exportApi.applications({ jobId: jobId || undefined, status: status || undefined });
                downloadBlob(r.data, `smarthire-applications-${new Date().toISOString().substring(0,10)}.csv`);
              }}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-[#1F2937] hover:bg-[#374151] text-gray-300 text-xs rounded-lg transition-colors">
              <Download size={13} /> Export CSV
            </button>
            <button
              onClick={async () => {
                const r = await exportApi.pipeline();
                downloadBlob(r.data, `smarthire-pipeline-${new Date().toISOString().substring(0,10)}.csv`);
              }}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-[#1F2937] hover:bg-[#374151] text-gray-300 text-xs rounded-lg transition-colors">
              <Download size={13} /> Pipeline CSV
            </button>
          </div>
        )}
      </div>

      {/* Filters — recruiter only */}
      {!isCandidate && (
        <div className="bg-[#111827] border border-[#1F2937] rounded-xl p-4">
          <div className="flex flex-wrap gap-3">
            <div className="relative flex-1 min-w-[200px]">
              <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" />
              <input value={search} onChange={e => setSearch(e.target.value)}
                placeholder="Search by candidate or role…"
                className="w-full pl-8 pr-3 py-2 bg-[#0B0F1A] border border-[#374151] rounded-lg text-sm text-white placeholder-gray-600 focus:outline-none focus:border-indigo-500" />
            </div>

            <select value={jobId} onChange={e => { setJobId(e.target.value); setPage(0); }}
              className="px-3 py-2 bg-[#0B0F1A] border border-[#374151] rounded-lg text-sm text-gray-300 focus:outline-none focus:border-indigo-500">
              <option value="">All Jobs</option>
              {jobsData?.content?.map((j: any) => (
                <option key={j.id} value={j.id}>{j.title}</option>
              ))}
            </select>

            <select value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}
              className="px-3 py-2 bg-[#0B0F1A] border border-[#374151] rounded-lg text-sm text-gray-300 focus:outline-none focus:border-indigo-500">
              {STATUS_OPTIONS.map(s => <option key={s} value={s}>{s || "All Statuses"}</option>)}
            </select>

            <div className="flex items-center gap-2">
              <Filter size={14} className="text-gray-500" />
              <input type="number" value={minScore} onChange={e => { setMinScore(e.target.value); setPage(0); }}
                placeholder="Min score" min={0} max={100}
                className="w-24 px-3 py-2 bg-[#0B0F1A] border border-[#374151] rounded-lg text-sm text-gray-300 focus:outline-none focus:border-indigo-500" />
            </div>
          </div>
        </div>
      )}

      {/* Bulk actions */}
      {!isCandidate && selectedIds.size > 0 && (
        <div className="flex items-center gap-3 bg-indigo-900/30 border border-indigo-800/50 rounded-xl px-4 py-3">
          <span className="text-sm text-indigo-300 font-medium">{selectedIds.size} selected</span>
          <select value={bulkStatus} onChange={e => setBulkStatus(e.target.value)}
            className="px-3 py-1.5 bg-[#0B0F1A] border border-[#374151] rounded text-sm text-gray-300 focus:outline-none focus:border-indigo-500">
            <option value="">Move to…</option>
            {["SCREENING","SHORTLISTED","INTERVIEW","OFFERED","REJECTED"].map(s => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
          <button disabled={!bulkStatus || bulkMutation.isPending}
            onClick={() => bulkMutation.mutate({ ids: Array.from(selectedIds), status: bulkStatus })}
            className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white text-sm rounded-lg transition-colors">
            {bulkMutation.isPending ? "Updating…" : "Apply"}
          </button>
          <button onClick={() => setSelectedIds(new Set())} className="text-sm text-gray-500 hover:text-gray-300">Cancel</button>
        </div>
      )}

      {/* Table */}
      <div className="bg-[#111827] border border-[#1F2937] rounded-xl overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center py-20">
            <div className="animate-spin rounded-full h-6 w-6 border-2 border-indigo-500 border-t-transparent" />
          </div>
        ) : filtered.length === 0 ? (
          <div className="text-center py-20">
            <p className="text-gray-500 text-sm">No applications found.</p>
            {isCandidate && (
              <Link to="/jobs" className="mt-3 inline-block text-indigo-400 hover:underline text-sm">
                Browse open positions →
              </Link>
            )}
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-[#1F2937]">
                {!isCandidate && <th className="text-left px-5 py-3 text-xs text-gray-500 font-medium">Candidate</th>}
                <th className="text-left px-5 py-3 text-xs text-gray-500 font-medium">Role</th>
                <th className="text-left px-5 py-3 text-xs text-gray-500 font-medium">Status</th>
                <th className="text-left px-5 py-3 text-xs text-gray-500 font-medium w-36">AI Score</th>
                {!isCandidate && <th className="text-left px-5 py-3 text-xs text-gray-500 font-medium">Signal</th>}
                <th className="text-left px-5 py-3 text-xs text-gray-500 font-medium">Applied</th>
                <th className="px-5 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-[#1F2937]">
              {filtered.map((app: any) => (
                <tr key={app.id} className="hover:bg-[#1F2937]/50 transition-colors group">
                  {!isCandidate && (
                    <td className="px-5 py-4">
                      <p className="font-medium text-white">{app.candidateName}</p>
                      <p className="text-xs text-gray-500">{app.candidateEmail}</p>
                    </td>
                  )}
                  <td className="px-5 py-4 text-gray-300">{app.jobTitle}</td>
                  <td className="px-5 py-4">
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${STATUS_COLORS[app.status] ?? "bg-gray-700 text-gray-400"}`}>
                      {app.status}
                    </span>
                  </td>
                  <td className="px-5 py-4 w-36">
                    <ScoreBar score={app.aiScore != null ? Number(app.aiScore) : null} />
                  </td>
                  {!isCandidate && (
                    <td className="px-5 py-4">
                      {app.recommendation ? (
                        <span className={`text-xs font-medium ${RECOMMENDATION_COLORS[app.recommendation] ?? "text-gray-400"}`}>
                          {app.recommendation.replace("_", " ")}
                        </span>
                      ) : <span className="text-xs text-gray-600">—</span>}
                    </td>
                  )}
                  <td className="px-5 py-4 text-gray-500 text-xs">
                    <span className="flex items-center gap-1">
                      <Clock size={11} />
                      {new Date(app.appliedAt).toLocaleDateString("en-IN")}
                    </span>
                  </td>
                  <td className="px-5 py-4">
                    <Link to={`/applications/${app.id}`}
                      className="flex items-center gap-1 text-indigo-400 hover:text-indigo-300 text-xs opacity-0 group-hover:opacity-100 transition-opacity">
                      View <ChevronRight size={12} />
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-center gap-2">
          <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
            className="px-3 py-1.5 bg-[#1F2937] hover:bg-[#374151] text-gray-300 text-sm rounded-lg disabled:opacity-40 transition-colors">
            ← Previous
          </button>
          <span className="px-3 py-1.5 text-gray-500 text-sm">
            Page {page + 1} of {totalPages}
          </span>
          <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
            className="px-3 py-1.5 bg-[#1F2937] hover:bg-[#374151] text-gray-300 text-sm rounded-lg disabled:opacity-40 transition-colors">
            Next →
          </button>
        </div>
      )}
    </div>
  );
}
