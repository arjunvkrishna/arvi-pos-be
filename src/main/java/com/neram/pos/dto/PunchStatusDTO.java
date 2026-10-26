package com.neram.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/** Whether the person is checked in right now (a shift is open), so the screen can offer Check In or Check Out, never both. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PunchStatusDTO {
    private boolean checkedIn;
    /** the check-in time of the open shift, when checked in */
    private LocalTime since;
    /** the date the open shift belongs to (yesterday for a shift that runs past midnight) */
    private LocalDate shiftDate;
}
