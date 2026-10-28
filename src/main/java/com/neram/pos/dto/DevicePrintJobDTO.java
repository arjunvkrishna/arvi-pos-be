package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One ticket the staff app prints itself, straight to the LAN printer. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DevicePrintJobDTO {
    private String jobId;
    private String printType;
    private String host;
    private Integer port;
    /** The ESC/POS bytes, Base64. */
    private String dataBase64;
}
