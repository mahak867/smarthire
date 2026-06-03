// ── SmartHire · frontend/src/pages/NewJob.tsx ──
import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { jobsApi } from "../utils/api";
import toast from "react-hot-toast";
import { ChevronLeft, Save, Send } from "lucide-react";

const EMPLOYMENT_TYPES = ["FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP"];
const TYPE_LABELS: Record<string, string> = {
  FULL_TIME: "Full-time", PART_TIME: "Part-time",
  CONTRACT: "Contract",   INTERNSHIP: "Internship",
};

interface FormState {
  title: string; description: string; requirements: string;
  department: string; location: string; employmentType: string;
  salaryMin: string; salaryMax: string; currency: string; deadline: string;
}

const INITIAL: FormState = {
  title: "", description: "", requirements: "",
  department: "", location: "", employmentType: "FULL_TIME",
  salaryMin: "", salaryMax: "", currency: "INR", deadline: "",
};

function Field({ label, required, error, children }: {
  label: string; required?: boolean; error?: string; children: React.ReactNode;
}) {
  return (
    <div>
      <label className="block text-xs font-medium text-gray-400 mb-1.5">
        {label}{required && <span className="text-red-400 ml-0.5">*</span>}
      </label>
      {children}
      {error && <p className="text-xs text-red-400 mt-1">{error}</p>}
    </div>
  );
}

const inputCls = "w-full bg-[#0B0F1A] border border-[#374151] rounded-lg px-3 py-2 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-indigo-500 transition-colors";
const textareaCls = inputCls + " resize-none";

export default function NewJob() {
  const { id: editId } = useParams<{ id?: string }>();
  const navigate    = useNavigate();
  const queryClient = useQueryClient();
  const isEditing   = !!editId;

  const [form, setForm] = useState<FormState>(INITIAL);
  const [errors, setErrors] = useState<Partial<FormState>>({});

  // Populate form when editing
  useQuery({
    queryKey: ["job", editId],
    queryFn: () => jobsApi.getById(editId!),
    enabled: isEditing,
    onSuccess: (job: any) => {
      setForm({
        title:          job.title ?? "",
        description:    job.description ?? "",
        requirements:   job.requirements ?? "",
        department:     job.department ?? "",
        location:       job.location ?? "",
        employmentType: job.employmentType ?? "FULL_TIME",
        salaryMin:      job.salaryMin?.toString() ?? "",
        salaryMax:      job.salaryMax?.toString() ?? "",
        currency:       job.currency ?? "INR",
        deadline:       job.deadline ? job.deadline.substring(0, 10) : "",
      });
    },
  });

  const set = (key: keyof FormState) => (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    setForm(prev => ({ ...prev, [key]: e.target.value }));
    setErrors(prev => ({ ...prev, [key]: undefined }));
  };

  const validate = (): boolean => {
    const e: Partial<FormState> = {};
    if (!form.title.trim() || form.title.length < 3) e.title = "Title must be at least 3 characters";
    if (!form.description.trim() || form.description.length < 50) e.description = "Description must be at least 50 characters";
    if (!form.requirements.trim() || form.requirements.length < 20) e.requirements = "Requirements must be at least 20 characters";
    if (!form.employmentType) e.employmentType = "Select an employment type";
    if (form.salaryMin && form.salaryMax && Number(form.salaryMin) > Number(form.salaryMax))
      e.salaryMax = "Maximum salary must be greater than minimum";
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const createMutation = useMutation({
    mutationFn: (publishNow: boolean) => jobsApi.create({ ...form,
      salaryMin: form.salaryMin ? Number(form.salaryMin) : undefined,
      salaryMax: form.salaryMax ? Number(form.salaryMax) : undefined,
      deadline: form.deadline || undefined,
      publishNow,
    }),
    onSuccess: (job: any) => {
      toast.success(job.status === "OPEN" ? "Job is now live!" : "Job saved as draft");
      queryClient.invalidateQueries({ queryKey: ["jobs"] });
      navigate(`/jobs/${job.id}`);
    },
    onError: (err: any) => toast.error(err?.response?.data?.error ?? "Failed to save job"),
  });

  const updateMutation = useMutation({
    mutationFn: () => jobsApi.update(editId!, { ...form,
      salaryMin: form.salaryMin ? Number(form.salaryMin) : undefined,
      salaryMax: form.salaryMax ? Number(form.salaryMax) : undefined,
      deadline: form.deadline || undefined,
    }),
    onSuccess: () => {
      toast.success("Job updated");
      queryClient.invalidateQueries({ queryKey: ["job", editId] });
      navigate(`/jobs/${editId}`);
    },
    onError: (err: any) => toast.error(err?.response?.data?.error ?? "Failed to update job"),
  });

  const handleSubmit = (publishNow: boolean) => {
    if (!validate()) return;
    if (isEditing) updateMutation.mutate();
    else createMutation.mutate(publishNow);
  };

  const isLoading = createMutation.isPending || updateMutation.isPending;

  return (
    <div className="max-w-2xl space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <button onClick={() => navigate("/jobs")}
          className="p-1.5 hover:bg-[#1F2937] rounded-lg transition-colors">
          <ChevronLeft size={18} className="text-gray-400" />
        </button>
        <h1 className="text-xl font-semibold text-white">
          {isEditing ? "Edit Job" : "Post a Job"}
        </h1>
      </div>

      {/* Form */}
      <div className="bg-[#111827] border border-[#1F2937] rounded-xl p-6 space-y-5">
        <Field label="Job Title" required error={errors.title}>
          <input value={form.title} onChange={set("title")} placeholder="e.g. Senior Backend Engineer"
            className={inputCls} />
        </Field>

        <div className="grid grid-cols-2 gap-4">
          <Field label="Department">
            <input value={form.department} onChange={set("department")} placeholder="e.g. Engineering"
              className={inputCls} />
          </Field>
          <Field label="Location">
            <input value={form.location} onChange={set("location")} placeholder="e.g. Bangalore / Remote"
              className={inputCls} />
          </Field>
        </div>

        <Field label="Employment Type" required error={errors.employmentType}>
          <div className="flex gap-2 flex-wrap">
            {EMPLOYMENT_TYPES.map(t => (
              <button key={t} onClick={() => setForm(p => ({ ...p, employmentType: t }))}
                className={`px-3 py-1.5 rounded-lg text-sm border transition-colors ${
                  form.employmentType === t
                    ? "bg-indigo-600 border-indigo-500 text-white"
                    : "bg-[#0B0F1A] border-[#374151] text-gray-400 hover:border-indigo-500"
                }`}>
                {TYPE_LABELS[t]}
              </button>
            ))}
          </div>
        </Field>

        <div className="grid grid-cols-3 gap-4">
          <Field label="Min Salary">
            <input type="number" value={form.salaryMin} onChange={set("salaryMin")}
              placeholder="e.g. 600000" className={inputCls} />
          </Field>
          <Field label="Max Salary" error={errors.salaryMax}>
            <input type="number" value={form.salaryMax} onChange={set("salaryMax")}
              placeholder="e.g. 1200000" className={inputCls} />
          </Field>
          <Field label="Currency">
            <select value={form.currency} onChange={set("currency")} className={inputCls}>
              {["INR","USD","GBP","EUR","AED"].map(c => <option key={c}>{c}</option>)}
            </select>
          </Field>
        </div>

        <Field label="Application Deadline">
          <input type="date" value={form.deadline} onChange={set("deadline")}
            min={new Date().toISOString().substring(0, 10)} className={inputCls} />
        </Field>

        <Field label="Job Description" required error={errors.description}>
          <textarea rows={6} value={form.description} onChange={set("description")}
            placeholder="Describe the role, responsibilities, and team context. Minimum 50 characters."
            className={textareaCls} />
          <p className="text-xs text-gray-600 mt-1">{form.description.length} / 50 min</p>
        </Field>

        <Field label="Requirements" required error={errors.requirements}>
          <textarea rows={5} value={form.requirements} onChange={set("requirements")}
            placeholder="List the skills, experience, and qualifications required. Be specific — our AI uses this text to score candidates."
            className={textareaCls} />
          <p className="text-xs text-gray-600 mt-1">{form.requirements.length} / 20 min · More detail = more accurate AI scoring</p>
        </Field>
      </div>

      {/* Actions */}
      <div className="flex gap-3">
        <button onClick={() => handleSubmit(false)} disabled={isLoading}
          className="flex items-center gap-2 px-4 py-2 bg-[#1F2937] hover:bg-[#374151] text-gray-300 text-sm rounded-lg transition-colors disabled:opacity-50">
          <Save size={15} /> {isEditing ? "Save Changes" : "Save as Draft"}
        </button>
        {!isEditing && (
          <button onClick={() => handleSubmit(true)} disabled={isLoading}
            className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-medium rounded-lg transition-colors disabled:opacity-50">
            <Send size={15} /> Publish Now
          </button>
        )}
      </div>
    </div>
  );
}
