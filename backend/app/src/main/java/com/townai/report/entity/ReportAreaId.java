package com.townai.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;

/**
 * Report와 Area의 연결 관계를 식별하는 복합 PK이다.
 *
 * <p>JPA Embedded ID 요구사항에 따라 {@link Serializable}, 값 기반
 * {@code equals/hashCode}, 보호된 기본 생성자를 제공한다.</p>
 */
@Embeddable
@Getter
@EqualsAndHashCode
public class ReportAreaId implements Serializable {

    /** 연결된 Report의 식별자이다. */
    @Column(name = "report_id")
    private Long reportId;

    /** 연결된 Area의 식별자이다. */
    @Column(name = "area_id")
    private Long areaId;

    /**
     * JPA가 복합 키를 복원할 때 사용할 빈 식별자를 생성한다.
     */
    protected ReportAreaId() {
    }

    /**
     * Report와 Area 식별자로 복합 키를 생성한다.
     *
     * @param reportId 연결할 Report ID
     * @param areaId 연결할 Area ID
     */
    public ReportAreaId(Long reportId, Long areaId) {
        this.reportId = reportId;
        this.areaId = areaId;
    }
}
