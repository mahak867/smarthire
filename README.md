# SmartHire — Console + Desktop GUI

A Recruitment Management System written in **core Java** with both a terminal interface and a Swing desktop GUI
(only `java.util`, `java.io`, `java.nio.file`, `java.security`, `java.time`
— no Spring, no database driver, no external libraries).

This is a from-scratch reimplementation of the original SmartHire project
(which was a full Spring Boot + JPA + JWT + Redis web application) as a
single-terminal, self-contained college project. Data is saved to local
flat files instead of a database, so it persists between runs without
needing any server or install step.

### Recent updates

- **Candidate skill profiles.** Candidates can save a skill list once
  (`s. Manage my skill profile`) and every place that asks for skills
  (apply, skill-gap check, recommendations) offers it as a one-Enter
  default instead of forcing a retype every time.
- **Edit a job posting.** Recruiters/admins could previously only
  close/reopen a job — `u. Edit a job posting` now lets them update
  title, department, description, skills, and employment type, with
  the same ownership check as close/reopen.
- **Applicant counts on job listings.** Every job table now shows how
  many applications a job has received, so you're not guessing job IDs
  blindly before drilling into "view applications for a job".
- **Applications now show candidate names, not just an ID.** Every
  application table a recruiter/admin sees (all applications, a job's
  applications, filtered-by-status, shortlist results, and the exported
  report) now resolves the candidate ID to a name — "Dev Candidate (#3)"
  instead of a bare "#3" you'd have to cross-reference against the user list.
- **Fixed a real data-corruption bug.** The storage format uses "|" to
  separate fields and ";" to separate skills within a field. A name, job
  title, department, or interviewer name containing either character
  (perfectly ordinary things to type - e.g. a title like "Backend | Frontend
  Dev") used to desync every field after it on the next save, which could
  throw an unrecoverable exception at startup on the *next* run and
  permanently block the app from starting until the corrupted line was
  found and fixed by hand. Every free-text field is now sanitized before
  being written (see `util/CsvUtil.java`), and this is now covered by a
  self-test.
- **Fixed a crash-the-session bug.** Entering a negative number for "how
  many candidates to shortlist" reached `Stream.limit()` with a negative
  argument, which throws `IllegalArgumentException` and (since that call
  sits below the input-collection layer) wasn't caught as a normal
  validation error - it silently ended the whole session instead of just
  showing an error and returning to the menu. Now validated up front with
  a normal, recoverable error message. Also covered by a self-test.
- **Fixed a stale-session bug.** Changing your password (or now, your
  skill profile) used to leave the in-memory session holding the old
  data — a second change in the same session could act on stale state.
  The session now refreshes immediately after either update.
- **Desktop GUI.** A Swing interface reuses the existing services and
  encrypted file-backed data. It includes login and registration,
  role-specific dashboards, candidate job search and applications,
  recruiter/admin job management, and applicant status updates.
- **Resume attachments.** Candidates can upload PDF or DOCX resumes from
  their profile (maximum 15 MB); recruiters can save a resume from an
  application for one of their own jobs. Resume contents and filenames are
  encrypted at rest with the app's AES-GCM key.
- **Excel-friendly job exchange.** Recruiters/admins can import and export
  UTF-8 CSV job postings from the jobs screen. CSV opens in Excel; imports
  create new OPEN postings owned by the signed-in recruiter/admin. Columns:
  Title, Department, Description, Required Skills, Employment Type; separate
  multiple required skills with semicolons. Types: FULL_TIME, PART_TIME,
  INTERNSHIP, CONTRACT.

## How to compile and run

Requires JDK 11+ and no build tool.

```bash
# from the project root; src/com contains the application and self-test runner
find src/com -name "*.java" > sources.txt
javac -d out @sources.txt
java -cp out com.smarthire.Main
```

On Windows (cmd):
```
dir /s /b src\com\*.java > sources.txt
javac -d out @sources.txt
java -cp out com.smarthire.Main
```

Launch the desktop GUI (after compiling):
```bash
java -cp out com.smarthire.gui.SmartHireGui
```

The GUI and console share `smarthire_data/`. Avoid running both at once
while changing data because each process loads the file-backed repositories
into memory at startup.

The app creates a `smarthire_data/` folder next to wherever you run it,
containing encrypted data files (`users.dat`, `jobs.dat`, `applications.dat`,
and `interviews.dat`), encrypted resume attachments under `resumes/`, plus
its encryption key and audit log.
Delete that folder to reset the app to a clean state.

On first run it seeds a default admin account:
```
email:    admin@smarthire.com
password: Admin@123
```

## Running the self-test suite

A lightweight, dependency-free test harness lives in `test/SelfTestRunner.java`,
using Java's built-in `assert` keyword. Run it with:
```bash
java -ea -cp out com.smarthire.test.SelfTestRunner
```
(`-ea` enables assertions — without it the tests silently no-op, which the
runner will warn you about.) It uses a throwaway `self_test_data/` folder
and never touches your real `smarthire_data/`.

## What makes this different from a generic CRUD clone

A login → post-job → apply → update-status console app is the obvious,
generic version of this brief. These push it further while staying inside
"core Java, terminal, standard library only":

1. **A real search engine, not `.contains()`.** `search/SearchEngine.java`
   builds an inverted index and ranks jobs with TF-IDF + cosine similarity —
   the same algorithm family real search engines use — from scratch with
   `HashMap`/`List`/regex.
2. **A real audit trail, not just a status field.** Every application status
   change is appended to an (encrypted) history log via
   `StatusHistoryRepository`, so you can see the whole timeline, not just
   where an application ended up.
3. **Real analytics.** The dashboard computes a hiring funnel (% of
   applications reaching each stage) and average time-to-hire in days.
4. **Data encrypted at rest.** `util/CryptoUtil.java` encrypts every data
   file with AES-128-GCM (`javax.crypto`, part of the standard JDK). A
   fresh random IV is generated per write; the key lives in
   `smarthire_data/secret.key`. Try `od -c smarthire_data/users.dat` — it's
   unreadable ciphertext, not the CSV you'd get from a naive project.
5. **A hand-written merge sort**, not just `Collections.sort()`.
   `util/Sorter.java` is a genuine recursive, stable, O(n log n)
   divide-and-conquer merge sort, used to rank applications by score and
   recommended jobs by match — a deliberate DSA showcase rather than
   leaning entirely on the standard library.
6. **A self-written test suite.** `test/SelfTestRunner.java` uses Java's
   built-in `assert` keyword (no JUnit dependency) to sanity-check
   password hashing, scoring, sorting, search relevance, the full
   apply→hire workflow, the encryption round-trip, skill-profile
   persistence across a reload, job-edit permission checks, CSV
   delimiter-injection safety, the shortlist non-positive-count guard,
   encrypted PDF/DOCX resume upload validation, and job CSV import/export
   round-tripping (12 tests total). Run it with:
   ```bash
   java -ea -cp out com.smarthire.test.SelfTestRunner
   ```
7. **Recommendations.** Candidates can ask for "Jobs you might like" —
   every open job ranked against their skills using the same scoring
   engine and merge sort used elsewhere.
8. **A saved candidate profile, not a stateless form.** Skills persist
   on the `User` record itself (`java.util.List<String>`, serialized
   alongside the rest of the user's data), so a returning candidate's
   apply/search/gap-check flows default to their saved profile instead
   of re-asking every time — small, but it's the difference between a
   form and a system that remembers who you are.

## Features

- **Auth**: register/login as Candidate or Recruiter (or use the seeded Admin).
  Passwords are salted and hashed with SHA-256 (`java.security.MessageDigest`)
  — never stored in plain text. Users can change their password.
- **Candidate skill profiles**: candidates can save a skill list once and
  reuse it everywhere skills are asked for (apply, skill-gap check,
  recommendations), with the option to override or update it per action.
- **Resume uploads**: candidates can attach one PDF or DOCX resume to their
  profile (up to 15 MB); a new upload replaces the previous file. Recruiters
  can save the resume from an applicant row for jobs they own. Files are
  encrypted locally and are never uploaded to a remote service.
- **Encrypted storage**: all data files are AES-128-GCM encrypted at rest
  (see point 4 above).
- **Jobs**: recruiters post jobs with title, description, department,
  required skills and employment type; can edit any of those fields later,
  close/reopen postings, and import/export postings as Excel-compatible
  UTF-8 CSV. Import columns: Title, Department, Description, Required Skills,
  Employment Type. New imported jobs start OPEN.
- **Search & recommendations**: substring search, ranked TF-IDF search, a
  skill-gap check before applying, and personalized "jobs you might like".
- **Applications**: candidates apply with a skills list and cover letter.
  A match score (0–100%) is computed automatically against the job's
  required skills — a transparent, explainable stand-in for the original
  project's "AI resume scoring" feature. Recruiters/admins see the
  applicant's name (not just an ID) in every applications table.
- **Shortlisting & status history**: recruiters can auto-shortlist the top N
  applicants by score (via the hand-written merge sort), or manually
  change any application's status, with every transition timestamped in a
  full, viewable history per application.
- **Interviews**: recruiters schedule interviews against an application,
  then record an outcome (feedback + hire/reject) once completed.
- **Dashboard & analytics**: status breakdown, hiring funnel, and average
  time-to-hire.
- **Reports**: dashboard stats and a job's applications can be exported to
  timestamped `.txt` files under `smarthire_data/reports/` (`java.io`).
- **Audit log**: every login, registration, job posting, application,
  status change, and password change is appended with a timestamp to
  `smarthire_data/audit.log`.
- **Formatted console tables**: aligned columns with ANSI color-coded
  statuses — no external library.
- **Role-based menus**: Admin / Recruiter / Candidate each see a different
  menu, with permission checks enforced in the service layer.

## Project structure

```
src/com/smarthire/
├── Main.java              # console entry point, menus, user I/O
├── model/                 # plain Java classes: User, Job, JobApplication,
│                           #   Interview, and their enums — each knows how
│                           #   to serialize/deserialize itself to CSV
├── repository/             # file-backed persistence, all encrypted at
│                           #   rest with AES-GCM (load into memory on
│                           #   startup, rewrite the file on every change)
├── search/                 # SearchEngine.java - from-scratch inverted
│                           #   index + TF-IDF + cosine-similarity search
├── service/                # business logic + validation:
│   ├── AuthService          #   register/login/change password
│   ├── JobService             #   post/close/list/search/recommend jobs
│   ├── ApplicationService      #   apply, shortlist, update status, history
│   ├── ScoringService            #   resume-vs-job keyword match + skill gap
│   ├── InterviewService           #   schedule/complete interviews
│   ├── DashboardService            #   funnel + time-to-hire analytics
│   ├── ExportService                #   writes report .txt files
│   ├── JobCsvService                 #   Excel-compatible job CSV exchange
│   └── ResumeService                  #   encrypted PDF/DOCX attachments
├── session/                # holds the logged-in user for this run
├── test/                   # SelfTestRunner.java - assert-based self-tests
└── util/                   # PasswordUtil (hashing), CryptoUtil (AES-GCM
                             #   encryption at rest), Sorter (merge sort),
                             #   Validator, Colors, ConsoleUI, TableRenderer,
                             #   AuditLogger
```

If your terminal doesn't render ANSI colors well (e.g. some Windows
`cmd.exe` versions, or when piping output to a file/grader), set
`Colors.ENABLED = false;` at the top of `Main.main()` — everything else
behaves identically, just without the color codes.

## Design notes (useful for your viva)

- **Why flat files instead of a database?** The brief required a terminal,
  core-Java app with no external dependencies. Each repository class
  loads its file into an in-memory `List` on startup and rewrites the
  whole file after every mutation — simple, transparent, and easy to
  inspect/explain (just open the `.txt` files).
- **Why keyword-overlap scoring instead of "real AI"?** It keeps the
  scoring logic fully explainable with core `java.util.Set` operations
  (`requiredSkills ∩ resumeSkills`), which is both honest about what it's
  doing and easy to defend in a viva, unlike a black-box model.
- **Where's the authentication token?** The original used JWT because it's
  a multi-request web API. A single terminal session doesn't need that —
  `Session.java` just holds the logged-in `User` object for the life of
  the process, which is the direct terminal equivalent.
- **Extending it**: the service layer is decoupled from `Main.java`, so a
  natural "future work" talking point is swapping the file-based
  repositories for JDBC/a real database without touching business logic.
