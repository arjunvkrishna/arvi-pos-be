package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "document_attachment_data")
public class DocumentAttachmentData {

    @Id
    @Column(name = "attachment_id")
    private Long attachmentId;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "file_data", nullable = false)
    private byte[] fileData;
}
