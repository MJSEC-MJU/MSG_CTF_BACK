package com.mjsec.ctf.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Path Traversal 공격 방어 유틸리티
 * OWASP 권장사항 기반
 */
@Slf4j
public class PathTraversalValidator {

    // Path Traversal 공격 패턴
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
        ".*(\\.\\./|\\.\\\\|%2e%2e/|%2e%2e\\\\|\\.\\.%2f|\\.\\.%5c|%252e%252e/|%252e%252e\\\\).*",
        Pattern.CASE_INSENSITIVE
    );

    // 절대 경로 패턴 (Windows, Linux)
    private static final Pattern ABSOLUTE_PATH_PATTERN = Pattern.compile(
        "^(/|\\\\|[a-zA-Z]:|%2f|%5c).*",
        Pattern.CASE_INSENSITIVE
    );

    // Null 바이트 인젝션 패턴
    private static final Pattern NULL_BYTE_PATTERN = Pattern.compile(
        ".*(\\x00|%00|\\\\0).*",
        Pattern.CASE_INSENSITIVE
    );

    // 위험한 파일 확장자 (이중 확장자 포함)
    private static final Pattern DANGEROUS_EXTENSION = Pattern.compile(
        ".*\\.(exe|sh|bat|cmd|com|pif|scr|vbs|js|jar|war|php|asp|aspx|jsp|dll|so).*",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * 파일명/경로에 Path Traversal 공격 시도가 있는지 검증
     *
     * @param filename 검증할 파일명 또는 경로
     * @return 안전하면 true, 위험하면 false
     */
    public static boolean isValidFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            log.warn("Path Traversal Check: Filename is null or empty");
            return false;
        }

        // 1. Path Traversal 패턴 체크 (../, ..\, 인코딩된 버전)
        if (PATH_TRAVERSAL_PATTERN.matcher(filename).matches()) {
            log.warn("🚨 Path Traversal Attack Detected: {} | Pattern: Directory Traversal", filename);
            return false;
        }

        // 2. 절대 경로 체크 (/, \, C:, 인코딩된 버전)
        if (ABSOLUTE_PATH_PATTERN.matcher(filename).matches()) {
            log.warn("🚨 Path Traversal Attack Detected: {} | Pattern: Absolute Path", filename);
            return false;
        }

        // 3. Null 바이트 인젝션 체크
        if (NULL_BYTE_PATTERN.matcher(filename).matches()) {
            log.warn("🚨 Path Traversal Attack Detected: {} | Pattern: Null Byte Injection", filename);
            return false;
        }

        // 4. 제어 문자 체크
        for (char c : filename.toCharArray()) {
            if (Character.isISOControl(c)) {
                log.warn("🚨 Path Traversal Attack Detected: {} | Pattern: Control Character", filename);
                return false;
            }
        }

        return true;
    }

    /**
     * 파일 확장자가 안전한지 검증
     *
     * @param filename 파일명
     * @return 안전하면 true, 위험하면 false
     */
    public static boolean isSafeExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        if (DANGEROUS_EXTENSION.matcher(filename).matches()) {
            log.warn("🚨 Dangerous File Extension Detected: {}", filename);
            return false;
        }

        return true;
    }

    /**
     * 파일명에서 위험한 문자 제거 (Sanitization)
     *
     * @param filename 원본 파일명
     * @return 안전하게 정리된 파일명
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "file";
        }

        // 1. 경로 구분자 제거
        String sanitized = filename.replace("/", "_")
                                  .replace("\\", "_")
                                  .replace("..", "_");

        // 2. 위험한 문자 제거 (영문, 숫자, -, _, . 만 허용)
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "_");

        // 3. 연속된 점 제거
        sanitized = sanitized.replaceAll("\\.{2,}", ".");

        // 4. 앞뒤 점 제거
        sanitized = sanitized.replaceAll("^\\.+|\\.+$", "");

        // 5. 빈 문자열이면 기본값 반환
        if (sanitized.isEmpty()) {
            return "file";
        }

        return sanitized;
    }

    /**
     * 파일 경로가 기준 디렉토리 내에 있는지 검증 (Canonical Path 비교)
     *
     * @param baseDirectory 기준 디렉토리
     * @param filePath 검증할 파일 경로
     * @return 안전하면 true, 위험하면 false
     */
    public static boolean isWithinDirectory(String baseDirectory, String filePath) {
        try {
            File base = new File(baseDirectory).getCanonicalFile();
            File file = new File(base, filePath).getCanonicalFile();

            // Canonical Path 비교로 심볼릭 링크 우회 방지
            if (!file.getCanonicalPath().startsWith(base.getCanonicalPath())) {
                log.warn("🚨 Path Traversal Attack Detected: {} escapes base directory {}",
                         filePath, baseDirectory);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("Path validation error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * UUID 형식인지 검증 (GCP Storage 파일 ID용)
     *
     * @param fileId 파일 ID
     * @return UUID 형식이면 true
     */
    public static boolean isValidUUID(String fileId) {
        if (fileId == null || fileId.isEmpty()) {
            return false;
        }

        // UUID 형식: 8-4-4-4-12 (예: 550e8400-e29b-41d4-a716-446655440000)
        Pattern uuidPattern = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE
        );

        return uuidPattern.matcher(fileId).matches();
    }

    /**
     * 안전한 파일명인지 종합 검증
     *
     * @param filename 파일명
     * @return 안전하면 true
     */
    public static boolean isSecureFilename(String filename) {
        return isValidFilename(filename) && isSafeExtension(filename);
    }
}
