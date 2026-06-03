// ── SmartHire · frontend/src/pages/Register.tsx ──
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation } from "@tanstack/react-query";
import { useNavigate, Link } from "react-router-dom";
import toast from "react-hot-toast";
import { authApi } from "../utils/api";

const schema = z.object({
  firstName: z.string().min(1, "First name is required.").max(100),
  lastName:  z.string().min(1, "Last name is required.").max(100),
  email:     z.string().email("Please enter a valid email address."),
  password:  z
    .string()
    .min(8,  "Password must be at least 8 characters.")
    .max(128, "Password must be under 128 characters.")
    .regex(/[A-Z]/,  "Password must contain at least one uppercase letter.")
    .regex(/\d/,     "Password must contain at least one digit."),
});
type FormData = z.infer<typeof schema>;

export default function Register() {
  const navigate = useNavigate();

  const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  const mutation = useMutation({
    mutationFn: (data: FormData) => authApi.register(data),
    onSuccess: () => {
      toast.success("Account created! Please sign in.");
      navigate("/login");
    },
  });

  const Field = ({ name, label, type = "text", placeholder }: {
    name: keyof FormData; label: string; type?: string; placeholder?: string;
  }) => (
    <div>
      <label className="text-xs text-gray-400 block mb-1.5">{label}</label>
      <input
        {...register(name)}
        type={type}
        placeholder={placeholder}
        className="w-full bg-[#1F2937] border border-[#374151] rounded-lg px-3 py-2.5 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-indigo-500 transition-colors"
      />
      {errors[name] && <p className="text-xs text-red-400 mt-1">{errors[name]?.message}</p>}
    </div>
  );

  return (
    <div className="min-h-screen bg-[#0B0F1A] flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="text-3xl font-bold text-indigo-500 mb-1">SmartHire</div>
          <p className="text-gray-400 text-sm">Create your candidate account</p>
        </div>

        <div className="bg-[#111827] border border-[#1F2937] rounded-2xl p-8">
          <h1 className="text-lg font-semibold text-white mb-6">Create account</h1>

          <form onSubmit={handleSubmit((d) => mutation.mutate(d))} className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <Field name="firstName" label="First name" placeholder="Jane" />
              <Field name="lastName"  label="Last name"  placeholder="Doe"  />
            </div>
            <Field name="email"    label="Email address"   type="email"    placeholder="jane@example.com" />
            <Field name="password" label="Password"         type="password" placeholder="Min 8 chars, 1 uppercase, 1 digit" />

            <button
              type="submit"
              disabled={mutation.isPending}
              className="w-full py-2.5 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white font-medium text-sm disabled:opacity-60 transition-colors mt-2"
            >
              {mutation.isPending ? "Creating account…" : "Create account"}
            </button>
          </form>

          <p className="text-center text-sm text-gray-500 mt-6">
            Already have an account?{" "}
            <Link to="/login" className="text-indigo-400 hover:text-indigo-300">Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
