package kr.suhsaechan.ai.util;

/**
 * 포맷팅 유틸리티 클래스
 * 바이트 크기, 시간 등을 사람이 읽기 쉬운 형식으로 변환합니다.
 */
public final class FormatUtils {

    private FormatUtils() {
        // 유틸리티 클래스 - 인스턴스화 방지
    }

    /**
     * 바이트 크기를 사람이 읽기 쉬운 형식으로 변환
     *
     * @param bytes 바이트 크기
     * @return 포맷된 문자열 (예: "1.23 GB", "456.78 MB")
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";

        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.2f KB", kb);

        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.2f MB", mb);

        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }

    /**
     * 바이트 크기를 사람이 읽기 쉬운 형식으로 변환 (null 허용)
     *
     * @param bytes 바이트 크기 (null 가능)
     * @return 포맷된 문자열 (null이면 "N/A")
     */
    public static String formatBytes(Long bytes) {
        if (bytes == null) return "N/A";
        return formatBytes(bytes.longValue());
    }

    /**
     * 밀리초를 사람이 읽기 쉬운 형식으로 변환
     *
     * @param durationMs 밀리초 단위 시간
     * @return 포맷된 문자열 (예: "2분 30초", "1시간 15분")
     */
    public static String formatDuration(long durationMs) {
        if (durationMs <= 0) {
            return "N/A";
        }

        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%d시간 %d분", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d분 %d초", minutes, seconds % 60);
        } else {
            return String.format("%d초", seconds);
        }
    }
}
