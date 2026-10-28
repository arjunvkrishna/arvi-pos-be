package com.neram.pos.service;

import com.neram.pos.dto.DevicePrintJobDTO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * "Print from this phone" for one request. While a scope is open on the request thread, {@code dispatchPrintJob} files each job as
 * CLAIMED by the phone (so the cloud bridge leaves it alone) and records its bytes here; the controller hands them back in the
 * response. If the phone never reports back, the claim lease expires and the normal sweep gives the job to the bridge.
 */
public final class DevicePrintScope implements AutoCloseable {

    /** How long the phone has to print and report before the cloud bridge may take the job over. */
    public static final int LEASE_SECONDS = 45;
    public static final String ID_PREFIX = "device:";
    private static final int MAX_ID_LENGTH = 100;
    private static final int MAX_SSID_LIST = 255;

    private static final ThreadLocal<DevicePrintScope> CURRENT = new ThreadLocal<>();

    private final String deviceId;
    private final List<DevicePrintJobDTO> jobs = new ArrayList<>();

    private DevicePrintScope(String deviceId) {
        this.deviceId = deviceId;
    }

    public static DevicePrintScope open(String deviceId) {
        DevicePrintScope scope = new DevicePrintScope(deviceId);
        CURRENT.set(scope);
        return scope;
    }

    public static DevicePrintScope current() {
        return CURRENT.get();
    }

    /** "device:&lt;username&gt;:&lt;phone id&gt;", safe to store in the claim column. */
    public static String deviceIdFor(String username, String phoneId) {
        String user = sanitise(username, 40);
        String phone = sanitise(phoneId, 24);
        String id = ID_PREFIX + (user.isEmpty() ? "staff" : user) + (phone.isEmpty() ? "" : ":" + phone);
        return id.length() > MAX_ID_LENGTH ? id.substring(0, MAX_ID_LENGTH) : id;
    }

    private static String sanitise(String raw, int max) {
        if (raw == null) return "";
        String cleaned = raw.replaceAll("[^A-Za-z0-9_.-]", "");
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }

    public String deviceId() {
        return deviceId;
    }

    public void add(DevicePrintJobDTO job) {
        jobs.add(job);
    }

    public List<DevicePrintJobDTO> jobs() {
        return List.copyOf(jobs);
    }

    /** Trims, drops blanks and duplicates (ignoring case), and keeps the list inside the column size. */
    public static String cleanSsidList(String raw) {
        if (raw == null) return "";
        String joined = Arrays.stream(raw.split("[,\\n]"))
                .map(String::trim)
                .filter(n -> !n.isEmpty())
                .collect(Collectors.toMap(n -> n.toLowerCase(java.util.Locale.ROOT), n -> n, (a, b) -> a, java.util.LinkedHashMap::new))
                .values().stream().collect(Collectors.joining(", "));
        return joined.length() > MAX_SSID_LIST ? joined.substring(0, MAX_SSID_LIST) : joined;
    }

    @Override
    public void close() {
        CURRENT.remove();
    }
}
