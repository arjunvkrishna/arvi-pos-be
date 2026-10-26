package com.neram.pos.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ESP32 firmware's heartbeat field names must reach the server, or the POS shows blanks. */
class PrintBridgeHeartbeatDTOTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void the_firmwares_ipAddress_field_is_the_bridge_ip_and_scan_support_is_read() throws Exception {
        PrintBridgeHeartbeatDTO hb = mapper.readValue(
                "{\"bridgeId\":\"B1\",\"ipAddress\":\"192.168.1.50\",\"scanSupported\":true,\"version\":\"v2.1.0-esp32\"}",
                PrintBridgeHeartbeatDTO.class);
        assertEquals("192.168.1.50", hb.getBridgeIp());
        assertTrue(hb.getScanSupported());
    }

    @Test
    void the_older_bridgeIp_name_still_works() throws Exception {
        PrintBridgeHeartbeatDTO hb = mapper.readValue("{\"bridgeId\":\"B1\",\"bridgeIp\":\"192.168.1.9\"}", PrintBridgeHeartbeatDTO.class);
        assertEquals("192.168.1.9", hb.getBridgeIp());
    }
}
