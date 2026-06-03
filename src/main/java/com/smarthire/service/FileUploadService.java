// ── SmartHire · src/main/java/com/smarthire/service/FileUploadService.java ──
package com.smarthire.service;

import com.smarthire.exception.BadRequestException;
import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class FileUploadService {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
    private static final List<String> ALLOWED_TYPES = List.of(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final MinioClient minioClient;
    private final String bucket;

    public FileUploadService(
            @Value("${smarthire.minio.endpoint}") String endpoint,
            @Value("${smarthire.minio.access-key}") String accessKey,
            @Value("${smarthire.minio.secret-key}") String secretKey,
            @Value("${smarthire.minio.bucket:smarthire-resumes}") String bucket) {

        this.minioClient = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();
        this.bucket = bucket;
        ensureBucketExists();
    }

    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.error("Failed to initialise MinIO bucket '{}': {}", bucket, e.getMessage());
        }
    }

    /**
     * Upload a resume file to MinIO.
     * Returns the object key (not a public URL — use getPresignedUrl for temporary access).
     */
    public String uploadResume(MultipartFile file, UUID candidateId) {
        validateFile(file);
        String extension = getExtension(file.getOriginalFilename());
        String objectKey  = "resumes/" + candidateId + "/" + UUID.randomUUID() + "." + extension;

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .stream(is, file.getSize(), -1)
                .contentType(file.getContentType())
                .userMetadata(java.util.Map.of(
                    "original-filename", file.getOriginalFilename() != null
                        ? file.getOriginalFilename() : "resume",
                    "candidate-id", candidateId.toString()
                ))
                .build());
            log.info("Resume uploaded: {} ({} bytes)", objectKey, file.getSize());
            return objectKey;
        } catch (Exception e) {
            log.error("MinIO upload failed: {}", e.getMessage(), e);
            throw new RuntimeException("Resume upload failed. Please try again in a moment.");
        }
    }

    /**
     * Generate a pre-signed URL valid for 1 hour for secure candidate resume access.
     */
    public String getPresignedUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .method(Method.GET)
                .expiry(1, TimeUnit.HOURS)
                .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for {}: {}", objectKey, e.getMessage());
            throw new RuntimeException("Could not generate resume access link. Please try again.");
        }
    }

    /**
     * Download a resume from MinIO and extract plain text using Apache PDFBox.
     * Falls back to an empty string on parse failure so scoring degrades gracefully.
     */
    public String extractResumeText(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return "";

        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {

            byte[] bytes = is.readAllBytes();

            if (objectKey.toLowerCase().endsWith(".pdf")) {
                return extractPdfText(bytes);
            }
            // For .doc/.docx: use Tika or fall back to raw bytes as UTF-8
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("[^\\x20-\\x7E\\n]", " ")
                .replaceAll("\\s{3,}", " ")
                .strip();

        } catch (Exception e) {
            log.warn("Could not extract text from resume {}: {}", objectKey, e.getMessage());
            return "";
        }
    }

    private String extractPdfText(byte[] bytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            // Normalise whitespace
            return text.replaceAll("\\r\\n?", "\n")
                       .replaceAll("[ \\t]{2,}", " ")
                       .strip();
        }
    }


    /**
     * Validates the actual file magic bytes, not just the content-type header.
     * Prevents MIME type spoofing (e.g. a .exe renamed to .pdf).
     */
    private void validateMagicBytes(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = is.readNBytes(8);
            if (!isPdf(header) && !isDocx(header) && !isDoc(header)) {
                throw new BadRequestException(
                    "File content does not match a recognised resume format. " +
                    "Please upload a genuine PDF or Word document.");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Magic bytes check failed: {}", e.getMessage());
        }
    }

    private boolean isPdf(byte[] h) {
        return h.length >= 4 &&
            h[0] == 0x25 && h[1] == 0x50 && h[2] == 0x44 && h[3] == 0x46; // %PDF
    }

    private boolean isDocx(byte[] h) {
        return h.length >= 4 &&
            h[0] == 0x50 && h[1] == 0x4B && h[2] == 0x03 && h[3] == 0x04; // PK zip (docx)
    }

    private boolean isDoc(byte[] h) {
        return h.length >= 8 &&
            h[0] == (byte)0xD0 && h[1] == (byte)0xCF &&
            h[2] == 0x11       && h[3] == (byte)0xE0; // OLE2 compound (doc)
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Resume file is required.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException(
                "Resume file exceeds the 5 MB limit. Please upload a smaller file.");
        }
        validateMagicBytes(file);
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BadRequestException(
                "Invalid file type. Only PDF and Word documents (.doc, .docx) are accepted.");
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "pdf";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "pdf";
    }
}
