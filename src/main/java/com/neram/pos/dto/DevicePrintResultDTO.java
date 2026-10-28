package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** What the phone saw when it tried to print a ticket itself. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DevicePrintResultDTO {
    /** PRINTED, NOT_SENT (nothing reached the printer) or UNSURE (the connection broke part-way). */
    private String outcome;
    private String message;
    private Integer bytesWritten;
}
