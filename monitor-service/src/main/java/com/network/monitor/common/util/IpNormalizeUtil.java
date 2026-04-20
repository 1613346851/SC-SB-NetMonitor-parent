package com.network.monitor.common.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class IpNormalizeUtil {

    private static final String IPV4_MAPPING_PREFIX = "::ffff:";

    public static String normalize(String ip) {
        if (ip == null || ip.isEmpty()) {
            return ip;
        }

        ip = ip.trim().toLowerCase();

        if (ip.contains(":")) {
            return normalizeIpv6(ip);
        } else {
            return normalizeIpv4(ip);
        }
    }

    private static String normalizeIpv4(String ip) {
        if (ip == null || ip.isEmpty()) {
            return ip;
        }

        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return ip;
        }

        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            try {
                int value = Integer.parseInt(parts[i]);
                if (value < 0 || value > 255) {
                    return ip;
                }
                normalized.append(value);
                if (i < 3) {
                    normalized.append(".");
                }
            } catch (NumberFormatException e) {
                return ip;
            }
        }

        return normalized.toString();
    }

    private static String normalizeIpv6(String ip) {
        if (ip == null || ip.isEmpty()) {
            return ip;
        }

        if (ip.startsWith(IPV4_MAPPING_PREFIX)) {
            String ipv4Part = ip.substring(IPV4_MAPPING_PREFIX.length());
            String normalizedIpv4 = normalizeIpv4(ipv4Part);
            if (normalizedIpv4 != null && normalizedIpv4.contains(".")) {
                return normalizedIpv4;
            }
            return ip;
        }

        if (ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1")) {
            return "127.0.0.1";
        }

        if (ip.equals("::") || ip.equals("0:0:0:0:0:0:0:0")) {
            return "0.0.0.0";
        }

        try {
            InetAddress address = InetAddress.getByName(ip);
            byte[] bytes = address.getAddress();

            if (bytes.length == 4) {
                return normalizeIpv4(ip);
            }

            StringBuilder result = new StringBuilder();

            for (int i = 0; i < bytes.length; i += 2) {
                int hex = ((bytes[i] & 0xff) << 8) | (bytes[i + 1] & 0xff);
                if (i > 0) {
                    result.append(":");
                }
                result.append(Integer.toHexString(hex));
            }

            String normalized = result.toString();
            normalized = compressZeros(normalized);

            return normalized;
        } catch (UnknownHostException e) {
            return ip;
        }
    }

    private static String compressZeros(String ip) {
        String[] parts = ip.split(":");
        int maxStart = -1;
        int maxLen = 0;
        int currentStart = -1;
        int currentLen = 0;

        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty() || parts[i].equals("0")) {
                if (currentStart == -1) {
                    currentStart = i;
                    currentLen = 1;
                } else {
                    currentLen++;
                }
            } else {
                if (currentLen > maxLen) {
                    maxStart = currentStart;
                    maxLen = currentLen;
                }
                currentStart = -1;
                currentLen = 0;
            }
        }

        if (currentLen > maxLen) {
            maxStart = currentStart;
            maxLen = currentLen;
        }

        if (maxStart == 0 || (maxStart == 1 && parts[0].isEmpty())) {
            maxLen++;
        }
        if (maxStart + maxLen == 8 || (maxStart + maxLen == 7 && !parts[parts.length - 1].isEmpty())) {
            maxLen++;
        }

        if (maxLen > 1) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < maxStart; i++) {
                if (i > 0) {
                    result.append(":");
                }
                result.append(parts[i]);
            }
            result.append("::");
            for (int i = maxStart + maxLen; i < parts.length; i++) {
                if (i > maxStart + maxLen) {
                    result.append(":");
                }
                result.append(parts[i]);
            }
            return result.toString();
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append(":");
            }
            result.append(parts[i].isEmpty() ? "0" : parts[i]);
        }
        return result.toString();
    }

    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        String normalized = normalize(ip);
        if (normalized == null) {
            return false;
        }

        if (normalized.contains(".")) {
            return isValidIpv4(normalized);
        } else if (normalized.contains(":")) {
            return isValidIpv6(normalized);
        }

        return false;
    }

    private static boolean isValidIpv4(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        for (String part : parts) {
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return true;
    }

    private static boolean isValidIpv6(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        if (ip.equals("::")) {
            return true;
        }

        try {
            InetAddress address = InetAddress.getByName(ip);
            byte[] bytes = address.getAddress();
            return bytes.length == 16;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public static boolean isIpv6(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        return ip.contains(":");
    }

    public static boolean isIpv4(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        return !ip.contains(":") && ip.contains(".");
    }
}