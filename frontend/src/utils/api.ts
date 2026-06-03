// ── SmartHire · frontend/src/utils/api.ts ──
import axios, { AxiosError, InternalAxiosRequestConfig } from "axios";
import toast from "react-hot-toast";
import { useAuthStore } from "../store/authStore";

const BASE_URL = import.meta.env.VITE_API_URL ?? "/api/v1";

export const api = axios.create({
  baseURL: BASE_URL,
  withCredentials: true,          // sends httpOnly refresh token cookie
  headers: { "Content-Type": "application/json" },
  timeout: 15_000,
});

// Attach access token to every request
api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let isRefreshing = false;
let failedQueue: Array<{ resolve: (v: unknown) => void; reject: (e: unknown) => void }> = [];

const processQueue = (error: AxiosError | null, token: string | null) => {
  failedQueue.forEach(({ resolve, reject }) =>
    error ? reject(error) : resolve(token)
  );
  failedQueue = [];
};

// Silent refresh on 401
api.interceptors.response.use(
  (r) => r,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & { _retry?: boolean };
    if (error.response?.status === 401 && !original._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((token) => {
          original.headers.Authorization = `Bearer ${token}`;
          return api(original);
        });
      }
      original._retry = true;
      isRefreshing = true;

      try {
        const { data } = await axios.post(`${BASE_URL}/auth/refresh`, {},
          { withCredentials: true });
        useAuthStore.getState().setAuth(data.user, data.accessToken);
        processQueue(null, data.accessToken);
        original.headers.Authorization = `Bearer ${data.accessToken}`;
        return api(original);
      } catch (refreshErr) {
        processQueue(refreshErr as AxiosError, null);
        useAuthStore.getState().clearAuth();
        window.location.href = "/login";
        return Promise.reject(refreshErr);
      } finally {
        isRefreshing = false;
      }
    }

    // Show user-friendly error toast
    const data = error.response?.data as Record<string, unknown> | undefined;
    const message = (data?.error as string) ??
      (error.response?.status === 429
        ? "Too many requests. Please slow down."
        : "Something went wrong. Please try again.");

    if (error.response?.status !== 401) {
      toast.error(message, { duration: 5000 });
    }
    return Promise.reject(error);
  }
);

// ── Typed API helpers ──────────────────────────────────────────────────────────

export const authApi = {
  login:          (data: { email: string; password: string }) =>
    api.post("/auth/login", data),
  register:       (data: { email: string; password: string; firstName: string; lastName: string }) =>
    api.post("/auth/register", data),
  logout:         (refreshToken: string) => api.post("/auth/logout", { refreshToken }),
  me:             () => api.get("/auth/me"),
  changePassword: (data: { currentPassword: string; newPassword: string }) =>
    api.post("/auth/change-password", data),
};

export const jobsApi = {
  list:    (params?: Record<string, unknown>) => api.get("/jobs", { params }).then(r => r.data),
  getById: (id: string)                       => api.get(`/jobs/${id}`).then(r => r.data),
  create:  (data: unknown)                    => api.post("/jobs", data).then(r => r.data),
  update:  (id: string, data: unknown)        => api.put(`/jobs/${id}`, data).then(r => r.data),
  archive:  (id: string)                      => api.delete(`/jobs/${id}`).then(r => r.data),
  publish:  (id: string)                      => api.post(`/jobs/${id}/publish`).then(r => r.data),
  my:      (params?: Record<string, unknown>) => api.get("/jobs/my", { params }),
};

export const applicationsApi = {
  submit:      (data: unknown)              => api.post("/applications", data),
  list:        (params?: Record<string, unknown>) => api.get("/applications", { params }),
  getById:     (id: string)                => api.get(`/applications/${id}`),
  updateStatus:(id: string, data: unknown) => api.put(`/applications/${id}/status`, data),
  my:          (params?: Record<string, unknown>) => api.get("/applications/my", { params }),
  ranked:      (jobId: string)             => api.get(`/applications/job/${jobId}/ranked`).then(r => r.data),
  bulkStatus: (ids: string[], status: string) => api.patch("/applications/bulk-status", { applicationIds: ids, status }).then(r => r.data),
  uploadResume: (formData: FormData)          => filesApi.uploadResume(formData),
};

export const dashboardApi = {
  stats:    () => api.get("/dashboard/stats"),
  pipeline: () => api.get("/dashboard/pipeline"),
  topCandidates: () => api.get("/dashboard/top-candidates"),
  funnel:   () => api.get("/dashboard/hiring-funnel"),
};

export const interviewsApi = {
  schedule:       (data: unknown)                        => api.post("/interviews", data).then(r => r.data),
  byApplication:  (applicationId: string)                => api.get(`/interviews/application/${applicationId}`).then(r => r.data),
  myInterviews:   ()                                     => api.get("/interviews/my").then(r => r.data),
  submitFeedback: (id: string, data: unknown)            => api.post(`/interviews/${id}/feedback`, data).then(r => r.data),
  cancel:         (id: string)                           => api.post(`/interviews/${id}/cancel`).then(r => r.data),
};

export const filesApi = {
  bulkStatus: (ids: string[], status: string) => api.patch("/applications/bulk-status", { applicationIds: ids, status }).then(r => r.data),
  uploadResume: (formData: FormData) =>
    api.post("/files/upload", formData, {
      headers: { "Content-Type": "multipart/form-data" },
      timeout: 30_000,
    }).then(r => r.data as { objectKey: string; message: string }),
  getResumeUrl: (objectKey: string) =>
    api.get(`/files/resume/${encodeURIComponent(objectKey)}`).then(r => r.data as { url: string }),
};

export const usersApi = {
  list:       (params?: Record<string, unknown>) => api.get("/users", { params }).then(r => r.data),
  getById:    (id: string)                       => api.get(`/users/${id}`).then(r => r.data),
  updateRole: (id: string, role: string)         => api.patch(`/users/${id}/role`, { role }).then(r => r.data),
  setActive:  (id: string, active: boolean)      => api.patch(`/users/${id}/status`, { active }).then(r => r.data),
  unlock:     (id: string)                       => api.post(`/users/${id}/unlock`).then(r => r.data),
};

export const gdprApi = {
  eraseMe:       (reason?: string) => api.delete("/gdpr/me", { data: { reason } }).then(r => r.data),
  exportMyData:  ()                => api.get("/gdpr/me/export").then(r => r.data),
};

export const exportApi = {
  applications: (params?: { jobId?: string; status?: string }) =>
    api.get("/export/applications", { params, responseType: "blob" }),
  pipeline: () =>
    api.get("/export/pipeline", { responseType: "blob" }),
};
