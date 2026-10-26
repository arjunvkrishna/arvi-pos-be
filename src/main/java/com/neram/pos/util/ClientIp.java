package com.neram.pos.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Best-effort real client address. The app sits behind nginx (and usually a Cloudflare tunnel), so the
 * socket address is a proxy. Preference: Cloudflare's header, then nginx's X-Real-IP, then the socket.
 * Used only for throttling / audit, never for authorisation.
 */
public final class ClientIp {

    private ClientIp() {}

    public static String of(HttpServletRequest request) {
        if (request == null) return "unknown";
        for (String header : new String[]{"CF-Connecting-IP", "X-Real-IP"}) {
            String v = request.getHeader(header);
            if (v != null && !v.isBlank() && !"unknown".equalsIgnoreCase(v.trim())) {
                return clean(v);
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : clean(remote);
    }

    private static String clean(String raw) {
        String v = raw.trim();
        int comma = v.indexOf(',');
        if (comma > 0) v = v.substring(0, comma).trim();
        return v.length() > 45 ? v.substring(0, 45) : v;
    }
}
