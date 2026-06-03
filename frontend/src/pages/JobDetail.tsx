// ── SmartHire · frontend/src/pages/JobDetail.tsx ──
import { useState } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { jobsApi, applicationsApi } from "../utils/api";
import { useAuthStore } from "../store/authStore";
import toast from "react-hot-toast";
import {
  MapPin, Briefcase, Clock, DollarSign, Users, Eye,
  ChevronLeft, Edit, Archive, Send, ExternalLink
} from "lucide-react";

const TYPE_LABELS: Record<string, string> = {
  FULL_TIME: "Full-time", PART_TIME: "Part-time",
  CONTRACT: "Contract",   INTERNSHIP: "Internship",
};

const STATUS_COLORS: Record<string, string> = {
  APPLIED:     "bg-blue-900/50 text-blue-300 border border-blue-800",
  SCREENING:   "bg-yellow-900/50 text-yellow-300 border border-yellow-800",
  SHORTLISTED: "bg-indigo-900/50 text-indigo-300 border border-indigo-800",
  INTERVIEW:   "bg-purple-900/50 text-purple-300 border border-purple-800",
  OFFERED:     "bg-emerald-900/50 text-emerald-300 border border-emerald-800",
  REJECTED:    "bg-red-900/50 text-red-300 border border-red-800",
};

function ScoreMeter({ score, label }: { score: number; label: string }) {
  const color = score >= 70 ? "bg-emerald-500" : score >= 50 ? "bg-yellow-500" : "bg-red-500";
  return (
    <div className="space-y-1">
      <div className="flex justify-between text-xs text-gray-400">
        <span>{label}</span><span>{score.toFixed(1)}%</span>
      </div>
      <div className="h-1.5 bg-[#1F2937] rounded-full overflow-hidden">
        <div className={`h-full rounded-full transition-all ${color}`} style={{ width: `${score}%` }} />
      </div>
    </div>
  );
}

export default function JobDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const queryClient = useQueryClient();
  const isRecruiter = user?.role === "RECRUITER" || user?.role === "ADMIN";

  const [applyOpen, setApplyOpen] = useState(false);
  const [resumeFile, setResumeFile] = useState<File | null>(null);
  const [coverLetter, setCoverLetter] = useState("");
  const [uploading, setUploading] = useState(false);

  const { data: job, isLoading } = useQuery({
    queryKey: ["job", id],
    queryFn: () => jobsApi.getById(id!),
    enabled: !!id,
  });

  const { data: applicationsData } = useQuery({
    queryKey: ["job-applications", id],
    queryFn: () => applicationsApi.list({ jobId: id, size: 50 }),
    enabled: !!id && isRecruiter,
  });

  const archiveMutation = useMutation({
    mutationFn: () => jobsApi.archive(id!),
    onSuccess: () => { toast.success("Job archived"); navigate("/jobs"); },
    onError: () => toast.error("Failed to archive job"),
  });

  const publishMutation = useMutation({
    mutationFn: () => jobsApi.publish(id!),
    onSuccess: () => {
      toast.success("Job is now live");
      queryClient.invalidateQueries({ queryKey: ["job", id] });
    },
  });

  const submitApplication = async () => {
    if (!resumeFile) { toast.error("Please upload your resume"); return; }
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append("file", resumeFile);
      const { objectKey } = await applicationsApi.uploadResume(formData);

      await applicationsApi.submit({
        jobId: id!,
        resumeUrl: objectKey,
        coverLetter: coverLetter.trim() || undefined,
      });

      toast.success("Application submitted! AI scoring is running in the background.");
      setApplyOpen(false);
      queryClient.invalidateQueries({ queryKey: ["my-applications"] });
    } catch (err: any) {
      toast.error(err?.response?.data?.error || "Submission failed. Please try again.");
    } finally {
      setUploading(false);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-2 border-indigo-500 border-t-transparent" />
      </div>
    );
  }

  if (!job) return (
    <div className="text-center py-20 text-gray-500">
      Job not found or has been removed.
      <div className="mt-4"><Link to="/jobs" className="text-indigo-400 hover:underline">← Back to jobs</Link></div>
    </div>
  );

  const applications = applicationsData?.content ?? [];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <button onClick={() => navigate("/jobs")}
          className="p-1.5 hover:bg-[#1F2937] rounded-lg transition-colors">
          <ChevronLeft size={18} className="text-gray-400" />
        </button>
        <h1 className="text-xl font-semibold text-white flex-1">{job.title}</h1>
        {isRecruiter && (
          <div className="flex gap-2">
            {job.status === "DRAFT" && (
              <button onClick={() => publishMutation.mutate()}
                className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white text-sm rounded-lg transition-colors">
                Publish
              </button>
            )}
            <button onClick={() => navigate(`/jobs/${id}/edit`)}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-[#1F2937] hover:bg-[#374151] text-gray-300 text-sm rounded-lg transition-colors">
              <Edit size={14} /> Edit
            </button>
            <button onClick={() => archiveMutation.mutate()}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-[#1F2937] hover:bg-red-900/40 text-gray-300 hover:text-red-400 text-sm rounded-lg transition-colors">
              <Archive size={14} /> Archive
            </button>
          </div>
        )}
      </div>

      <div className="grid grid-cols-3 gap-6">
        {/* Main content */}
        <div className="col-span-2 space-y-5">
          {/* Meta */}
          <div className="bg-[#111827] border border-[#1F2937] rounded-xl p-5">
            <div className="flex flex-wrap gap-4 text-sm text-gray-400 mb-5">
              {job.location && <span className="flex items-center gap-1.5"><MapPin size={14} />{job.location}</span>}
              <span className="flex items-center gap-1.5"><Briefcase size={14} />{TYPE_LABELS[job.employmentType] ?? job.employmentType}</span>
              {(job.salaryMin || job.salaryMax) && (
                <span className="flex items-center gap-1.5">
                  <DollarSign size={14} />
                  {job.currency === "INR"
                    ? `₹${((job.salaryMin ?? 0) / 100000).toFixed(1)}L${job.salaryMax ? ` – ₹${(job.salaryMax / 100000).toFixed(1)}L` : "+"}`
                    : `$${Math.round((job.salaryMin ?? 0) / 1000)}k${job.salaryMax ? ` – $${Math.round(job.salaryMax / 1000)}k` : "+"}`
                  }
                </span>
              )}
              <span className="flex items-center gap-1.5"><Eye size={14} />{job.viewsCount} views</span>
              {isRecruiter && <span className="flex items-center gap-1.5"><Users size={14} />{job.applicationCount} applications</span>}
            </div>

            <div className="space-y-5">
              <div>
                <h3 className="text-sm font-medium text-gray-300 mb-2">Description</h3>
                <p className="text-sm text-gray-400 whitespace-pre-line leading-relaxed">{job.description}</p>
              </div>
              <div>
                <h3 className="text-sm font-medium text-gray-300 mb-2">Requirements</h3>
                <p className="text-sm text-gray-400 whitespace-pre-line leading-relaxed">{job.requirements}</p>
              </div>
            </div>
          </div>

          {/* Recruiter: applications list */}
          {isRecruiter && applications.length > 0 && (
            <div className="bg-[#111827] border border-[#1F2937] rounded-xl">
              <div className="px-5 py-4 border-b border-[#1F2937]">
                <h3 className="text-sm font-medium text-white">Applications ({job.applicationCount})</h3>
              </div>
              <div className="divide-y divide-[#1F2937]">
                {applications.map((app: any) => (
                  <Link key={app.id} to={`/applications/${app.id}`}
                    className="flex items-center gap-4 px-5 py-4 hover:bg-[#1F2937]/50 transition-colors group">
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-white truncate">{app.candidateName}</p>
                      <p className="text-xs text-gray-500 truncate">{app.candidateEmail}</p>
                    </div>
                    <div className="w-32 space-y-1">
                      {app.aiScore != null ? (
                        <ScoreMeter score={Number(app.aiScore)} label="AI Score" />
                      ) : (
                        <p className="text-xs text-gray-600 italic">Scoring…</p>
                      )}
                    </div>
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${STATUS_COLORS[app.status] ?? "bg-gray-800 text-gray-400"}`}>
                      {app.status}
                    </span>
                    <ExternalLink size={14} className="text-gray-600 group-hover:text-indigo-400 transition-colors shrink-0" />
                  </Link>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div className="space-y-4">
          <div className="bg-[#111827] border border-[#1F2937] rounded-xl p-5 space-y-3">
            <div className="flex justify-between text-sm">
              <span className="text-gray-500">Status</span>
              <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                job.status === "OPEN" ? "bg-emerald-900/50 text-emerald-300 border border-emerald-800" :
                job.status === "DRAFT" ? "bg-yellow-900/50 text-yellow-300 border border-yellow-800" :
                "bg-gray-700 text-gray-400"}`}>{job.status}</span>
            </div>
            {job.department && (
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">Department</span>
                <span className="text-gray-300">{job.department}</span>
              </div>
            )}
            {job.deadline && (
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">Deadline</span>
                <span className="text-gray-300">{new Date(job.deadline).toLocaleDateString("en-IN")}</span>
              </div>
            )}
            <div className="flex justify-between text-sm">
              <span className="text-gray-500">Posted by</span>
              <span className="text-gray-300 truncate max-w-[140px]">{job.postedByName}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-gray-500">Posted</span>
              <span className="text-gray-300">{new Date(job.createdAt).toLocaleDateString("en-IN")}</span>
            </div>
          </div>

          {/* Candidate: Apply button */}
          {!isRecruiter && job.status === "OPEN" && (
            <button onClick={() => setApplyOpen(true)}
              className="w-full py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-medium rounded-lg transition-colors flex items-center justify-center gap-2">
              <Send size={15} /> Apply Now
            </button>
          )}
        </div>
      </div>

      {/* Apply modal */}
      {applyOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
          <div className="bg-[#111827] border border-[#1F2937] rounded-xl p-6 w-full max-w-md mx-4 shadow-2xl">
            <h2 className="text-base font-semibold text-white mb-4">Apply for {job.title}</h2>

            <div className="space-y-4">
              <div>
                <label className="block text-xs text-gray-400 mb-1.5">Resume (PDF or Word, max 5 MB) *</label>
                <input type="file" accept=".pdf,.doc,.docx"
                  onChange={e => setResumeFile(e.target.files?.[0] ?? null)}
                  className="w-full text-sm text-gray-400 file:mr-3 file:py-1.5 file:px-3 file:rounded file:border-0 file:text-xs file:bg-indigo-600 file:text-white hover:file:bg-indigo-700 cursor-pointer" />
                {resumeFile && <p className="text-xs text-emerald-400 mt-1">{resumeFile.name}</p>}
              </div>

              <div>
                <label className="block text-xs text-gray-400 mb-1.5">Cover letter (optional)</label>
                <textarea rows={4} value={coverLetter} onChange={e => setCoverLetter(e.target.value)}
                  placeholder="Tell us why you're a great fit for this role…"
                  className="w-full bg-[#0B0F1A] border border-[#374151] rounded-lg px-3 py-2 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-indigo-500 resize-none" />
              </div>

              <p className="text-xs text-gray-600">
                Your resume will be analysed by our AI scoring engine. Results appear in the application detail within ~30 seconds.
              </p>
            </div>

            <div className="flex gap-3 mt-5">
              <button onClick={() => setApplyOpen(false)}
                className="flex-1 py-2 bg-[#1F2937] hover:bg-[#374151] text-gray-300 text-sm rounded-lg transition-colors">
                Cancel
              </button>
              <button onClick={submitApplication} disabled={uploading || !resumeFile}
                className="flex-1 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium rounded-lg transition-colors">
                {uploading ? "Uploading…" : "Submit Application"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
