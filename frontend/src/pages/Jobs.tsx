// ── SmartHire · frontend/src/pages/Jobs.tsx ──
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { jobsApi } from "../utils/api";
import { useAuthStore } from "../store/authStore";
import { Search, Plus, MapPin, Briefcase, Clock, ChevronRight } from "lucide-react";

interface Job {
  id: string;
  title: string;
  department: string | null;
  location: string | null;
  employmentType: string;
  salaryMin: number | null;
  salaryMax: number | null;
  currency: string;
  status: string;
  viewsCount: number;
  applicationCount: number;
  createdAt: string;
  deadline: string | null;
}

interface JobsResponse {
  content: Job[];
  totalElements: number;
  totalPages: number;
  page: number;
}

const STATUS_OPTIONS = ["", "OPEN", "DRAFT", "CLOSED", "ARCHIVED"];
const TYPE_OPTIONS   = ["", "FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP"];

const TYPE_LABELS: Record<string, string> = {
  FULL_TIME: "Full-time", PART_TIME: "Part-time",
  CONTRACT: "Contract", INTERNSHIP: "Internship",
};

const STATUS_COLORS: Record<string, string> = {
  OPEN:     "bg-emerald-900 text-emerald-300",
  DRAFT:    "bg-yellow-900 text-yellow-300",
  CLOSED:   "bg-gray-700 text-gray-400",
  ARCHIVED: "bg-gray-800 text-gray-500",
};

function formatSalary(min: number | null, max: number | null, currency: string) {
  if (!min && !max) return null;
  const fmt = (n: number) =>
    currency === "INR"
      ? `₹${(n / 100000).toFixed(1)}L`
      : `$${Math.round(n / 1000)}k`;
  if (min && max) return `${fmt(min)} – ${fmt(max)}`;
  if (min) return `From ${fmt(min)}`;
  return `Up to ${fmt(max!)}`;
}

export default function Jobs() {
  const { user } = useAuthStore();
  const navigate = useNavigate();
  const isRecruiter = user?.role === "RECRUITER" || user?.role === "ADMIN";

  const [page,   setPage]   = useState(0);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [type,   setType]   = useState("");

  const params = { page, size: 20, search: search || undefined,
    status: status || undefined, employmentType: type || undefined };

  const { data, isLoading } = useQuery<{ data: JobsResponse }>({
    queryKey: ["jobs", params],
    queryFn:  () => jobsApi.list(params),
    staleTime: 30_000,
  });

  const jobs = data?.data?.content ?? [];
  const totalPages = data?.data?.totalPages ?? 0;

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-white">Job postings</h1>
          <p className="text-sm text-gray-400 mt-0.5">
            {data?.data?.totalElements ?? "—"} positions
          </p>
        </div>
        {isRecruiter && (
          <Link to="/jobs/new"
            className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-medium transition-colors">
            <Plus size={16} /> New job
          </Link>
        )}
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3">
        <div className="relative flex-1 min-w-[200px]">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" />
          <input
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            placeholder="Search jobs…"
            className="w-full pl-9 pr-3 py-2 bg-[#111827] border border-[#1F2937] rounded-lg text-sm text-white placeholder-gray-500 focus:outline-none focus:border-indigo-500"
          />
        </div>
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}
          className="bg-[#111827] border border-[#1F2937] rounded-lg px-3 py-2 text-sm text-gray-300 focus:outline-none focus:border-indigo-500">
          {STATUS_OPTIONS.map((s) => <option key={s} value={s}>{s || "All statuses"}</option>)}
        </select>
        <select value={type} onChange={(e) => { setType(e.target.value); setPage(0); }}
          className="bg-[#111827] border border-[#1F2937] rounded-lg px-3 py-2 text-sm text-gray-300 focus:outline-none focus:border-indigo-500">
          {TYPE_OPTIONS.map((t) => (
            <option key={t} value={t}>{t ? TYPE_LABELS[t] : "All types"}</option>
          ))}
        </select>
      </div>

      {/* Job list */}
      {isLoading ? (
        <div className="space-y-3">
          {[...Array(5)].map((_, i) => (
            <div key={i} className="h-24 bg-[#111827] rounded-xl animate-pulse border border-[#1F2937]" />
          ))}
        </div>
      ) : jobs.length === 0 ? (
        <div className="text-center py-16 text-gray-500">
          <Briefcase size={32} className="mx-auto mb-3 opacity-30" />
          <p className="text-sm">No jobs found matching your filters.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {jobs.map((job) => {
            const salary = formatSalary(job.salaryMin, job.salaryMax, job.currency);
            const isExpired = job.deadline && new Date(job.deadline) < new Date();
            return (
              <div key={job.id}
                onClick={() => navigate(`/jobs/${job.id}`)}
                className="bg-[#111827] border border-[#1F2937] hover:border-indigo-500/50 rounded-xl p-5 cursor-pointer transition-all group">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <h2 className="text-white font-medium group-hover:text-indigo-400 transition-colors truncate">
                        {job.title}
                      </h2>
                      <span className={`text-xs px-2 py-0.5 rounded-full shrink-0 ${STATUS_COLORS[job.status] ?? "bg-gray-700 text-gray-400"}`}>
                        {job.status}
                      </span>
                    </div>
                    <div className="flex flex-wrap items-center gap-3 text-xs text-gray-400">
                      {job.department && (
                        <span className="flex items-center gap-1">
                          <Briefcase size={11} /> {job.department}
                        </span>
                      )}
                      {job.location && (
                        <span className="flex items-center gap-1">
                          <MapPin size={11} /> {job.location}
                        </span>
                      )}
                      <span className="bg-[#1F2937] px-2 py-0.5 rounded">
                        {TYPE_LABELS[job.employmentType] ?? job.employmentType}
                      </span>
                      {salary && <span className="text-emerald-400">{salary}</span>}
                      {job.deadline && (
                        <span className={`flex items-center gap-1 ${isExpired ? "text-red-400" : ""}`}>
                          <Clock size={11} />
                          {isExpired ? "Expired" : `Deadline ${new Date(job.deadline).toLocaleDateString()}`}
                        </span>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center gap-4 shrink-0">
                    {isRecruiter && (
                      <div className="text-right">
                        <div className="text-white text-sm font-medium">{job.applicationCount}</div>
                        <div className="text-gray-500 text-xs">applicants</div>
                      </div>
                    )}
                    <ChevronRight size={16} className="text-gray-600 group-hover:text-indigo-400 transition-colors" />
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 pt-2">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="px-3 py-1.5 text-sm text-gray-400 border border-[#1F2937] rounded-lg disabled:opacity-40 hover:border-indigo-500 transition-colors"
          >
            Previous
          </button>
          <span className="text-sm text-gray-500">Page {page + 1} of {totalPages}</span>
          <button
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            disabled={page >= totalPages - 1}
            className="px-3 py-1.5 text-sm text-gray-400 border border-[#1F2937] rounded-lg disabled:opacity-40 hover:border-indigo-500 transition-colors"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
