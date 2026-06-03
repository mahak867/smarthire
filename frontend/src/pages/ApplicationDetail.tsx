// ── SmartHire · frontend/src/pages/ApplicationDetail.tsx ──
import { useParams, useNavigate } from "react-router-dom";
import { useAiScoring } from "../hooks/useAiScoring";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import toast from "react-hot-toast";
import { applicationsApi } from "../utils/api";
import { useAuthStore } from "../store/authStore";
import { CheckCircle, XCircle, AlertCircle, Clock, ChevronRight } from "lucide-react";

// ── Types ────────────────────────────────────────────────────────────────────

interface Application {
  id: string;
  jobTitle: string;
  candidateName: string;
  candidateEmail: string;
  resumeUrl: string;
  coverLetter: string | null;
  status: string;
  aiScore: number | null;
  aiSummary: string | null;
  skillMatchPct: number | null;
  scoringComplete: boolean;
  keywordMatches: {
    scoring_method?: string;
    tfidf_score?: number;
    claude_score?: number;
    strengths?: string[];
    gaps?: string[];
    recommendation?: string;
  } | null;
  appliedAt: string;
}

// ── Pipeline stepper ─────────────────────────────────────────────────────────

const PIPELINE_STAGES = ["APPLIED","SCREENING","SHORTLISTED","INTERVIEW","OFFERED"];

function PipelineStepper({ current }: { current: string }) {
  const idx = PIPELINE_STAGES.indexOf(current);
  const isRejected = current === "REJECTED" || current === "WITHDRAWN";

  return (
    <div className="flex items-center gap-1">
      {PIPELINE_STAGES.map((stage, i) => (
        <div key={stage} className="flex items-center gap-1">
          <div className={`flex flex-col items-center`}>
            <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-medium
              ${isRejected && i <= idx ? "bg-red-600 text-white"
                : i < idx ? "bg-indigo-600 text-white"
                : i === idx ? "bg-indigo-500 text-white ring-2 ring-indigo-400 ring-offset-2 ring-offset-[#111827]"
                : "bg-[#374151] text-gray-400"}`}>
              {i < idx && !isRejected ? <CheckCircle size={14} /> : i + 1}
            </div>
            <span className="text-[10px] text-gray-500 mt-1 whitespace-nowrap">{stage}</span>
          </div>
          {i < PIPELINE_STAGES.length - 1 && (
            <div className={`h-0.5 w-8 mb-4 ${i < idx ? "bg-indigo-600" : "bg-[#374151]"}`} />
          )}
        </div>
      ))}
      {isRejected && (
        <div className="ml-3 flex items-center gap-1">
          <XCircle size={16} className="text-red-400" />
          <span className="text-xs text-red-400">{current}</span>
        </div>
      )}
    </div>
  );
}

// ── AI Score Card ─────────────────────────────────────────────────────────────

function AIScoreCard({ app }: { app: Application }) {
  if (!app.scoringComplete) {
    return (
      <div className="bg-[#111827] border border-[#1F2937] rounded-xl p-5">
        <div className="flex items-center gap-2 text-yellow-400">
          <Clock size={16} className="animate-pulse" />
          <span className="text-sm font-medium">AI scoring in progress…</span>
        </div>
        <p className="text-xs text-gray-500 mt-2">
          Our AI engine is analysing the resume against the job description.
          Results usually appear within 30 seconds.
        </p>
      </div>
    );
  }

  const score       = app.aiScore ?? 0;
  const skillMatch  = app.skillMatchPct ?? 0;
  const tfidf       = app.keywordMatches?.tfidf_score ?? 0;
  const strengths   = app.keywordMatches?.strengths ?? [];
  const gaps        = app.keywordMatches?.gaps ?? [];
  const rec         = app.keywordMatches?.recommendation ?? "—";
  const method      = app.keywordMatches?.scoring_method ?? "tfidf_only";

  const recColor = {
    STRONG_YES: "text-emerald-400", YES: "text-green-400",
    MAYBE: "text-yellow-400", NO: "text-red-400",
  }[rec] ?? "text-gray-400";

  const ScoreBar = ({ pct, color }: { pct: number; color: string }) => (
    <div className="w-full bg-[#374151] rounded-full h-2">
      <div
        className="h-2 rounded-full transition-all duration-700"
        style={{ width: `${Math.max(pct, 0)}%`, background: color }}
      />
    </div>
  );

  return (
    <div className="bg-[#111827] border border-[#1F2937] rounded-xl p-5 space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-white uppercase tracking-wider">AI Match Score</h3>
        <span className="text-xs text-gray-500 bg-[#1F2937] px-2 py-0.5 rounded">
          {method === "claude+tfidf" ? "Claude + TF-IDF" : "TF-IDF only"}
        </span>
      </div>

      {/* Overall score */}
      <div>
        <div className="flex items-end gap-2 mb-2">
          <span className="text-4xl font-bold text-white">{score.toFixed(1)}</span>
          <span className="text-gray-400 text-lg mb-1">/100</span>
        </div>
        <ScoreBar pct={score} color="#6366F1" />
      </div>

      {/* Sub-scores */}
      <div className="space-y-3">
        <div>
          <div className="flex justify-between text-xs text-gray-400 mb-1">
            <span>Skill Match</span>
            <span className="text-white font-medium">{skillMatch.toFixed(1)}%</span>
          </div>
          <ScoreBar pct={skillMatch} color="#10B981" />
        </div>
        <div>
          <div className="flex justify-between text-xs text-gray-400 mb-1">
            <span>TF-IDF Similarity</span>
            <span className="text-white font-medium">{tfidf.toFixed(1)}%</span>
          </div>
          <ScoreBar pct={tfidf} color="#8B5CF6" />
        </div>
      </div>

      {/* Strengths & gaps */}
      {strengths.length > 0 && (
        <div>
          <p className="text-xs text-gray-500 uppercase tracking-wider mb-1.5">Strengths</p>
          <div className="flex flex-wrap gap-1.5">
            {strengths.map((s) => (
              <span key={s} className="text-xs bg-emerald-900/60 text-emerald-300 px-2 py-0.5 rounded">
                {s}
              </span>
            ))}
          </div>
        </div>
      )}
      {gaps.length > 0 && (
        <div>
          <p className="text-xs text-gray-500 uppercase tracking-wider mb-1.5">Gaps</p>
          <div className="flex flex-wrap gap-1.5">
            {gaps.map((g) => (
              <span key={g} className="text-xs bg-red-900/60 text-red-300 px-2 py-0.5 rounded">
                {g}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Recommendation */}
      <div className="border-t border-[#1F2937] pt-3">
        <div className="flex items-center justify-between">
          <span className="text-xs text-gray-400">Recommendation</span>
          <span className={`text-sm font-semibold ${recColor}`}>{rec.replace("_", " ")}</span>
        </div>
        {app.aiSummary && (
          <p className="text-xs text-gray-400 mt-2 leading-relaxed italic">"{app.aiSummary}"</p>
        )}
      </div>
    </div>
  );
}

// ── Status Update Form ────────────────────────────────────────────────────────

const statusSchema = z.object({
  status: z.string().min(1),
  notes:  z.string().max(1000).optional(),
});

const VALID_STATUSES = ["APPLIED","SCREENING","SHORTLISTED","INTERVIEW","OFFERED","REJECTED","WITHDRAWN"];

function StatusUpdateForm({ appId, currentStatus }: { appId: string; currentStatus: string }) {
  const qc = useQueryClient();
  const { register, handleSubmit } = useForm({ resolver: zodResolver(statusSchema),
    defaultValues: { status: currentStatus } });

  const mutation = useMutation({
    mutationFn: (data: { status: string; notes?: string }) =>
      applicationsApi.updateStatus(appId, data),
    onSuccess: () => {
      toast.success("Application status updated");
      qc.invalidateQueries({ queryKey: ["application", appId] });
    },
  });

  return (
    <form onSubmit={handleSubmit((d) => mutation.mutate(d))} className="space-y-3">
      <div>
        <label className="text-xs text-gray-400 block mb-1">Update status</label>
        <select {...register("status")}
          className="w-full bg-[#1F2937] border border-[#374151] rounded-lg px-3 py-2 text-sm text-white">
          {VALID_STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>
      <div>
        <label className="text-xs text-gray-400 block mb-1">Notes (optional)</label>
        <textarea {...register("notes")} rows={2}
          className="w-full bg-[#1F2937] border border-[#374151] rounded-lg px-3 py-2 text-sm text-white resize-none"
          placeholder="Internal notes for the hiring team…" />
      </div>
      <button type="submit" disabled={mutation.isPending}
        className="w-full py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-medium disabled:opacity-50 transition-colors">
        {mutation.isPending ? "Updating…" : "Update status"}
      </button>
    </form>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function ApplicationDetail() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuthStore();
  const isRecruiter = user?.role === "RECRUITER" || user?.role === "ADMIN";

  const { data, isLoading, error } = useQuery<{ data: Application }>({
    queryKey: ["application", id],
    queryFn:  () => applicationsApi.getById(id!),
    refetchInterval: (query) =>
      query.state.data?.data?.scoringComplete === false ? 5000 : false,
  });

  if (isLoading) return (
    <div className="flex items-center justify-center h-64">
      <div className="animate-spin rounded-full h-8 w-8 border-2 border-indigo-500 border-t-transparent" />
    </div>
  );

  if (error || !data) return (
    <div className="text-center py-16 text-gray-400">
      <AlertCircle size={32} className="mx-auto mb-3 text-red-400" />
      <p>Application not found or you do not have access.</p>
    </div>
  );

  const app = data.data;

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <div>
        <h1 className="text-xl font-semibold text-white">{app.candidateName}</h1>
        <p className="text-sm text-gray-400">{app.jobTitle} · Applied {new Date(app.appliedAt).toLocaleDateString()}</p>
      </div>

      {/* Pipeline stepper */}
      <div className="bg-[#111827] border border-[#1F2937] rounded-xl p-5">
        <PipelineStepper current={app.status} />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left col: candidate info */}
        <div className="lg:col-span-2 space-y-5">
          <div className="bg-[#111827] border border-[#1F2937] rounded-xl p-5 space-y-3">
            <h2 className="text-sm font-semibold text-white">Candidate information</h2>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div><p className="text-gray-500 text-xs">Name</p><p className="text-white">{app.candidateName}</p></div>
              <div><p className="text-gray-500 text-xs">Email</p><p className="text-white">{app.candidateEmail}</p></div>
              <div><p className="text-gray-500 text-xs">Resume</p>
                <a href={app.resumeUrl} target="_blank" rel="noreferrer"
                  className="text-indigo-400 hover:underline text-sm">View PDF ↗</a>
              </div>
              <div><p className="text-gray-500 text-xs">Applied</p>
                <p className="text-white">{new Date(app.appliedAt).toLocaleString()}</p>
              </div>
            </div>
            {app.coverLetter && (
              <div>
                <p className="text-gray-500 text-xs mb-1">Cover letter</p>
                <p className="text-sm text-gray-300 leading-relaxed">{app.coverLetter}</p>
              </div>
            )}
          </div>

          {isRecruiter && (
            <div className="bg-[#111827] border border-[#1F2937] rounded-xl p-5">
              <h2 className="text-sm font-semibold text-white mb-4">Update pipeline</h2>
              <StatusUpdateForm appId={app.id} currentStatus={app.status} />
            </div>
          )}
        </div>

        {/* Right col: AI score */}
        <div>
          <AIScoreCard app={app} />
        </div>
      </div>
    </div>
  );
}
