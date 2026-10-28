package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "report_export_data")
public class ReportExportData {

    @Id
    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "file_data", nullable = false)
    private byte[] fileData;
}
