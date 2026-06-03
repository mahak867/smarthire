// ── SmartHire · frontend/src/pages/Dashboard.tsx ──
import { useQuery } from "@tanstack/react-query";
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer,
  FunnelChart, Funnel, LabelList, Cell,
} from "recharts";
import { dashboardApi, applicationsApi } from "../utils/api";
import { Briefcase, Users, TrendingUp, Clock } from "lucide-react";

// ── Types ────────────────────────────────────────────────────────────────────

interface Stats {
  totalOpenJobs: number;
  applicationsThisWeek: number;
  shortlistRatePct: number;
  avgTimeToHireDays: number;
  totalApplications: number;
}

interface TopCandidate {
  id: string;
  candidateName: string;
  candidateEmail: string;
  jobTitle: string;
  aiScore: number | null;
  skillMatchPct: number | null;
  status: string;
}

// ── Stat Card ─────────────────────────────────────────────────────────────────

function StatCard({ label, value, sub, Icon, color }: {
  label: string; value: string | number; sub: string;
  Icon: React.ElementType; color: string;
}) {
  return (
    <div className="bg-[#111827] border border-[#1F2937] rounded-xl p-5">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs text-gray-400 uppercase tracking-wider mb-1">{label}</p>
          <p className="text-2xl font-semibold text-white">{value}</p>
          <p className="text-xs text-gray-500 mt-1">{sub}</p>
        </div>
        <div className={`p-2.5 rounded-lg ${color}`}>
          <Icon size={18} className="text-white" />
        </div>
      </div>
    </div>
  );
}

// ── Signal Badge ──────────────────────────────────────────────────────────────

const STATUS_COLORS: Record<string, string> = {
  APPLIED:     "bg-blue-900 text-blue-300",
  SCREENING:   "bg-yellow-900 text-yellow-300",
  SHORTLISTED: "bg-indigo-900 text-indigo-300",
  INTERVIEW:   "bg-purple-900 text-purple-300",
  OFFERED:     "bg-emerald-900 text-emerald-300",
  REJECTED:    "bg-red-900 text-red-300",
  WITHDRAWN:   "bg-gray-700 text-gray-300",
};

// ── Main Dashboard ────────────────────────────────────────────────────────────

export default function Dashboard() {
  const { data: stats }    = useQuery<{ data: Stats }>({
    queryKey: ["dashboard-stats"],
    queryFn:  dashboardApi.stats,
    staleTime: 30_000,
  });

  const { data: pipeline } = useQuery<{ data: Record<string, number> }>({
    queryKey: ["dashboard-pipeline"],
    queryFn:  dashboardApi.pipeline,
    staleTime: 30_000,
  });

  const { data: funnel }   = useQuery<{ data: Array<{ stage: string; count: number; pct_of_total: number }> }>({
    queryKey: ["dashboard-funnel"],
    queryFn:  dashboardApi.funnel,
    staleTime: 60_000,
  });

  const { data: topCandidates } = useQuery<{ data: TopCandidate[] }>({
    queryKey: ["dashboard-top-candidates"],
    queryFn:  dashboardApi.topCandidates,
    staleTime: 60_000,
  });

  const s = stats?.data;
  const pipelineData = pipeline?.data
    ? Object.entries(pipeline.data).map(([stage, count]) => ({ stage, count }))
    : [];

  const funnelData = funnel?.data?.map((f, i) => ({
    name:  f.stage,
    value: f.count,
    pct:   f.pct_of_total,
    fill:  ["#6366F1","#8B5CF6","#A78BFA","#10B981","#34D399"][i] ?? "#6366F1",
  })) ?? [];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold text-white">Hiring dashboard</h1>
        <p className="text-sm text-gray-400 mt-0.5">
          Real-time pipeline analytics · AI-scored candidates
        </p>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard label="Open positions"    value={s?.totalOpenJobs ?? "—"}         sub="actively hiring"         Icon={Briefcase}  color="bg-indigo-600" />
        <StatCard label="Apps this week"    value={s?.applicationsThisWeek ?? "—"}   sub="new applicants"          Icon={Users}      color="bg-purple-600" />
        <StatCard label="Shortlist rate"    value={s ? `${s.shortlistRatePct.toFixed(1)}%` : "—"} sub="of total applicants" Icon={TrendingUp} color="bg-emerald-600" />
        <StatCard label="Avg time to hire"  value={s ? `${s.avgTimeToHireDays}d` : "—"} sub="calendar days"       Icon={Clock}      color="bg-blue-600"   />
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
        {/* Pipeline bar chart */}
        <div className="bg-[#111827] border border-[#1F2937] rounded-xl p-5">
          <h2 className="text-sm font-medium text-white mb-4">Pipeline by status</h2>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={pipelineData} barSize={28}>
              <XAxis dataKey="stage" tick={{ fill: "#6B7280", fontSize: 11 }} />
              <YAxis tick={{ fill: "#6B7280", fontSize: 11 }} />
              <Tooltip
                contentStyle={{ background: "#1F2937", border: "none", borderRadius: 8 }}
                labelStyle={{ color: "#E5E7EB" }}
                itemStyle={{ color: "#A5B4FC" }}
              />
              <Bar dataKey="count" fill="#6366F1" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Hiring funnel */}
        <div className="bg-[#111827] border border-[#1F2937] rounded-xl p-5">
          <h2 className="text-sm font-medium text-white mb-4">Hiring funnel</h2>
          <div className="space-y-2">
            {funnelData.map((f) => (
              <div key={f.name} className="flex items-center gap-3">
                <span className="text-xs text-gray-400 w-24 shrink-0">{f.name}</span>
                <div className="flex-1 bg-[#1F2937] rounded-full h-2">
                  <div
                    className="h-2 rounded-full transition-all duration-500"
                    style={{ width: `${Math.max(f.pct, 2)}%`, background: f.fill }}
                  />
                </div>
                <span className="text-xs text-gray-300 w-10 text-right">{f.value}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Top candidates table */}
      <div className="bg-[#111827] border border-[#1F2937] rounded-xl overflow-hidden">
        <div className="px-5 py-4 border-b border-[#1F2937]">
          <h2 className="text-sm font-medium text-white">Top candidates by AI score</h2>
          <p className="text-xs text-gray-500 mt-0.5">Across all open positions</p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-xs text-gray-500 uppercase tracking-wider">
                <th className="text-left px-5 py-3">Candidate</th>
                <th className="text-left px-5 py-3">Position</th>
                <th className="text-left px-5 py-3">AI score</th>
                <th className="text-left px-5 py-3">Skill match</th>
                <th className="text-left px-5 py-3">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#1F2937]">
              {topCandidates?.data?.map((c) => (
                <tr key={c.id} className="hover:bg-[#1F2937] transition-colors">
                  <td className="px-5 py-3">
                    <div className="text-white font-medium">{c.candidateName}</div>
                    <div className="text-xs text-gray-500">{c.candidateEmail}</div>
                  </td>
                  <td className="px-5 py-3 text-gray-300">{c.jobTitle}</td>
                  <td className="px-5 py-3">
                    <div className="flex items-center gap-2">
                      <div className="w-16 bg-[#374151] rounded-full h-1.5">
                        <div
                          className="h-1.5 rounded-full bg-indigo-500"
                          style={{ width: `${c.aiScore ?? 0}%` }}
                        />
                      </div>
                      <span className="text-white text-xs font-medium">
                        {c.aiScore != null ? `${c.aiScore.toFixed(1)}` : "—"}
                      </span>
                    </div>
                  </td>
                  <td className="px-5 py-3 text-gray-300 text-xs">
                    {c.skillMatchPct != null ? `${c.skillMatchPct.toFixed(1)}%` : "—"}
                  </td>
                  <td className="px-5 py-3">
                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${STATUS_COLORS[c.status] ?? "bg-gray-700 text-gray-300"}`}>
                      {c.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
