// ── SmartHire · src/main/java/com/smarthire/service/EmailService.java ──
package com.smarthire.service;

import com.smarthire.domain.entities.Application;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${smarthire.mail.from}")
    private String fromAddress;

    @Value("${smarthire.mail.from-name}")
    private String fromName;

    @Async("emailExecutor")
    public void sendApplicationStatusUpdate(Application app) {
        try {
            String to      = app.getCandidate().getEmail();
            String name    = app.getCandidate().getFirstName();
            String jobTitle = app.getJob().getTitle();
            String status  = app.getStatus().name();

            String subject = statusSubject(status, jobTitle);
            String body    = statusBody(name, jobTitle, status);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(buildHtml(subject, body), true);

            mailSender.send(message);
            log.info("Status email sent to {} for application {} (status: {})",
                to, app.getId(), status);

        } catch (Exception e) {
            log.error("Failed to send status email for application {}: {}", app.getId(), e.getMessage());
            // Non-fatal — email failure should not break the status update flow
        }
    }

    private String statusSubject(String status, String jobTitle) {
        return switch (status) {
            case "SHORTLISTED" -> "Great news! You've been shortlisted for " + jobTitle;
            case "INTERVIEW"   -> "Interview invitation for " + jobTitle;
            case "OFFERED"     -> "Job offer for " + jobTitle + " — Congratulations!";
            case "REJECTED"    -> "Your application for " + jobTitle;
            case "WITHDRAWN"   -> "Application withdrawn — " + jobTitle;
            default            -> "Application update for " + jobTitle;
        };
    }

    private String statusBody(String name, String jobTitle, String status) {
        return switch (status) {
            case "SHORTLISTED" -> "Hi %s, we're pleased to let you know that you've been shortlisted for the %s position. Our team will be in touch shortly to arrange next steps."
                .formatted(name, jobTitle);
            case "INTERVIEW"   -> "Hi %s, we'd like to invite you for an interview for the %s role. Please check your SmartHire dashboard to select an available time slot."
                .formatted(name, jobTitle);
            case "OFFERED"     -> "Hi %s, congratulations! We're delighted to extend an offer for the %s position. Please log in to SmartHire to review your offer details."
                .formatted(name, jobTitle);
            case "REJECTED"    -> "Hi %s, thank you for your interest in the %s role. After careful consideration, we have decided to move forward with other candidates. We encourage you to apply for future openings."
                .formatted(name, jobTitle);
            default            -> "Hi %s, your application for %s has been updated. Please log in to SmartHire to see the latest status."
                .formatted(name, jobTitle);
        };
    }

    private String buildHtml(String subject, String body) {
        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family:Arial,sans-serif;background:#f5f5f5;padding:20px">
              <div style="max-width:600px;margin:0 auto;background:#fff;border-radius:8px;padding:32px">
                <div style="font-size:20px;font-weight:bold;color:#6366F1;margin-bottom:16px">SmartHire</div>
                <h2 style="color:#111;font-size:18px;margin-bottom:16px">%s</h2>
                <p style="color:#444;line-height:1.6;font-size:14px">%s</p>
                <div style="margin-top:24px;padding-top:16px;border-top:1px solid #eee">
                  <a href="https://smarthire.app" style="display:inline-block;padding:10px 20px;background:#6366F1;color:#fff;border-radius:6px;text-decoration:none;font-size:14px">View on SmartHire</a>
                </div>
                <p style="color:#999;font-size:12px;margin-top:24px">You're receiving this because you applied through SmartHire. To unsubscribe, update your notification settings in your profile.</p>
              </div>
            </body>
            </html>
            """.formatted(subject, body);
    }
/** Notify a candidate that an interview has been scheduled. */
    public void sendInterviewScheduled(
            com.smarthire.domain.entities.User candidate,
            String jobTitle,
            java.time.OffsetDateTime scheduledAt,
            String interviewType,
            String meetingLink) {

        if (candidate.getEmail() == null) return;
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(String.format("%s <%s>", fromName, from));
            msg.setTo(candidate.getEmail());
            msg.setSubject("Interview scheduled — " + jobTitle);
            msg.setText(String.format(
                "Hi %s,\n\nYour %s interview for %s has been scheduled.\n\n" +
                "Date & Time: %s\n%s\n\n" +
                "Please be on time and have your questions ready.\n\nBest,\nThe SmartHire Team",
                candidate.getFirstName(),
                interviewType.toLowerCase(),
                jobTitle,
                scheduledAt.toZonedDateTime()
                    .withZoneSameInstant(java.time.ZoneId.of("Asia/Kolkata"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy 'at' hh:mm a z")),
                meetingLink != null ? "Meeting link: " + meetingLink : ""
            ));
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("Interview scheduled email failed for {}: {}", candidate.getEmail(), e.getMessage());
        }
    }

    /** Notify a candidate that their interview has been cancelled. */
    public void sendInterviewCancelled(
            com.smarthire.domain.entities.User candidate,
            String jobTitle,
            java.time.OffsetDateTime scheduledAt) {

        if (candidate.getEmail() == null) return;
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(String.format("%s <%s>", fromName, from));
            msg.setTo(candidate.getEmail());
            msg.setSubject("Interview cancelled — " + jobTitle);
            msg.setText(String.format(
                "Hi %s,\n\nUnfortunately your interview for %s scheduled on %s has been cancelled.\n\n" +
                "The hiring team will reach out to reschedule. We apologise for the inconvenience.\n\nBest,\nThe SmartHire Team",
                candidate.getFirstName(), jobTitle,
                scheduledAt.toZonedDateTime()
                    .withZoneSameInstant(java.time.ZoneId.of("Asia/Kolkata"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"))
            ));
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("Interview cancelled email failed for {}: {}", candidate.getEmail(), e.getMessage());
        }
    }
    /** Acknowledge receipt of an application after 7 days still in APPLIED state. */
    public void sendApplicationAcknowledgement(
            com.smarthire.domain.entities.User candidate, String jobTitle) {
        if (candidate.getEmail() == null) return;
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(String.format("%s <%s>", fromName, from));
            msg.setTo(candidate.getEmail());
            msg.setSubject("Your application is being reviewed — " + jobTitle);
            msg.setText(String.format(
                "Hi %s,\n\nWe wanted to let you know that your application for %s is currently under review.\n\n" +
                "We will notify you as soon as there is an update. Thank you for your patience.\n\nBest,\nThe SmartHire Team",
                candidate.getFirstName(), jobTitle
            ));
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("Ack email failed for {}: {}", candidate.getEmail(), e.getMessage());
        }
    }

}