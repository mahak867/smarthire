package com.smarthire.service;

import com.smarthire.model.*;
import com.smarthire.repository.JobRepository;
import com.smarthire.search.SearchEngine;
import com.smarthire.util.Sorter;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class JobService {

    private final JobRepository jobRepository;
    private final SearchEngine searchEngine = new SearchEngine();

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public Job postJob(User postedBy, String title, String description, String department,
                        List<String> requiredSkills, EmploymentType type) {
        requireRecruiterOrAdmin(postedBy);
        if (title == null || title.trim().isEmpty()) throw new SmartHireException("Title cannot be empty.");

        Job job = new Job(jobRepository.nextId(), title, description, department, requiredSkills,
                type, postedBy.getId(), JobStatus.OPEN, LocalDate.now());
        jobRepository.save(job);
        return job;
    }

    public void closeJob(User actor, int jobId) {
        Job job = getById(jobId);
        requireOwnerOrAdmin(actor, job.getRecruiterId());
        job.setStatus(JobStatus.CLOSED);
        jobRepository.save(job);
    }

    public void reopenJob(User actor, int jobId) {
        Job job = getById(jobId);
        requireOwnerOrAdmin(actor, job.getRecruiterId());
        job.setStatus(JobStatus.OPEN);
        jobRepository.save(job);
    }

    /**
     * Updates an existing job's fields in place. The caller (Main.java) is responsible
     * for resolving "leave blank to keep current" input into final values - this method
     * just applies whatever it's given, after checking permission.
     */
    public Job editJob(User actor, int jobId, String title, String description, String department,
                        List<String> requiredSkills, EmploymentType type) {
        Job job = getById(jobId);
        requireOwnerOrAdmin(actor, job.getRecruiterId());
        if (title == null || title.trim().isEmpty()) throw new SmartHireException("Title cannot be empty.");
        job.setTitle(title);
        job.setDescription(description);
        job.setDepartment(department);
        job.setRequiredSkills(requiredSkills);
        job.setEmploymentType(type);
        jobRepository.save(job);
        return job;
    }

    public Job getById(int jobId) {
        return jobRepository.findById(jobId).orElseThrow(() -> new SmartHireException("Job #" + jobId + " not found."));
    }

    public List<Job> listOpenJobs() {
        return jobRepository.findAll().stream()
                .filter(j -> j.getStatus() == JobStatus.OPEN)
                .collect(Collectors.toList());
    }

    public List<Job> listAllJobs() {
        return jobRepository.findAll();
    }

    public List<Job> listByRecruiter(int recruiterId) {
        return jobRepository.findByRecruiter(recruiterId);
    }

    /** Case-insensitive substring search across title, department, and required skills of OPEN jobs. */
    public List<Job> searchOpenJobs(String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        return jobRepository.findAll().stream()
                .filter(j -> j.getStatus() == JobStatus.OPEN)
                .filter(j -> kw.isEmpty()
                        || j.getTitle().toLowerCase().contains(kw)
                        || j.getDepartment().toLowerCase().contains(kw)
                        || j.getRequiredSkills().stream().anyMatch(s -> s.toLowerCase().contains(kw)))
                .collect(Collectors.toList());
    }

    /**
     * Ranked, relevance-scored search over OPEN jobs using a from-scratch
     * TF-IDF + cosine-similarity search engine (see search.SearchEngine).
     * Unlike searchOpenJobs(), this ranks results by how well they match the
     * query overall, not just whether a substring happens to appear.
     */
    public List<SearchEngine.SearchResult> rankedSearchOpenJobs(String query) {
        List<Job> openJobs = jobRepository.findAll().stream()
                .filter(j -> j.getStatus() == JobStatus.OPEN)
                .collect(Collectors.toList());
        searchEngine.build(openJobs);
        return searchEngine.search(query);
    }

    /**
     * "Jobs you might like": ranks all OPEN jobs by how well a candidate's
     * skill set matches each job's required skills, using the same scoring
     * engine as application matching. Returns the top N by score.
     */
    public List<Job> recommendJobs(List<String> candidateSkills, ScoringService scoringService, int limit) {
        List<Job> openJobs = jobRepository.findAll().stream()
                .filter(j -> j.getStatus() == JobStatus.OPEN)
                .collect(Collectors.toList());

        List<Job> ranked = Sorter.mergeSort(openJobs, Comparator.comparingDouble(
                (Job j) -> scoringService.computeScore(candidateSkills, j.getRequiredSkills())).reversed());

        return ranked.stream().limit(limit).collect(Collectors.toList());
    }

    private void requireRecruiterOrAdmin(User user) {
        if (user.getRole() != UserRole.RECRUITER && user.getRole() != UserRole.ADMIN) {
            throw new SmartHireException("Only recruiters or admins can post jobs.");
        }
    }

    private void requireOwnerOrAdmin(User actor, int ownerId) {
        if (actor.getRole() != UserRole.ADMIN && actor.getId() != ownerId) {
            throw new SmartHireException("You do not have permission to modify this job.");
        }
    }
}
