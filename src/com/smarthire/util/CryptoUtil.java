package com.smarthire.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Encrypts SmartHire's data files at rest using AES-128-GCM
 * (javax.crypto - part of the standard JDK, not an external library).
 *
 * On first run a random 128-bit key is generated and stored in
 * smarthire_data/secret.key. Every write generates a fresh random 12-byte
 * IV (required for GCM - reusing an IV with the same key breaks the
 * confidentiality guarantee), which is stored alongside the ciphertext.
 *
 * Honest caveat for the viva: storing the key next to the data it protects
 * means this defends against someone browsing the data files directly (e.g.
 * copying users.txt off a shared machine), not against someone with full
 * access to the whole smarthire_data/ folder. A production system would
 * keep the key in a separate secret store (env var, OS keychain, KMS) -
 * this demonstrates the encryption mechanics core Java provides, at the
 * scope appropriate for a terminal college project.
 */
public class CryptoUtil {

    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;

    public CryptoUtil(String dataDir) {
        this.key = loadOrCreateKey(Paths.get(dataDir, "secret.key"));
    }

    private SecretKey loadOrCreateKey(Path keyFile) {
        try {
            Files.createDirectories(keyFile.getParent());
            if (Files.exists(keyFile)) {
                byte[] raw = hexToBytes(new String(Files.readAllBytes(keyFile), StandardCharsets.UTF_8).trim());
                return new SecretKeySpec(raw, "AES");
            }
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(128, new SecureRandom());
            SecretKey generated = kg.generateKey();
            Files.write(keyFile, bytesToHex(generated.getEncoded()).getBytes(StandardCharsets.UTF_8));
            return generated;
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize encryption key", e);
        }
    }

    /** Encrypts the given lines and writes them as a single encrypted blob to file. */
    public void writeEncryptedLines(Path file, List<String> lines) {
        try {
            String joined = String.join("\n", lines);
            byte[] plaintext = joined.getBytes(StandardCharsets.UTF_8);

            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            Files.write(file, combined, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("Could not write encrypted file: " + file, e);
        }
    }

    /** Reads and decrypts a file written by writeEncryptedLines(). Returns an empty list for a new/empty file. */
    public List<String> readDecryptedLines(Path file) {
        try {
            if (!Files.exists(file) || Files.size(file) == 0) return new ArrayList<>();
            byte[] combined = Files.readAllBytes(file);
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            String joined = new String(plaintext, StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>();
            if (!joined.isEmpty()) {
                for (String line : joined.split("\n", -1)) {
                    if (!line.isEmpty()) lines.add(line);
                }
            }
            return lines;
        } catch (Exception e) {
            throw new RuntimeException("Could not read/decrypt file: " + file
                    + " (wrong key, or file corrupted/tampered with)", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
