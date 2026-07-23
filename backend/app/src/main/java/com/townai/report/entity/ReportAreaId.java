package com.townai.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ReportAreaId implements Serializable {

    /** 연결된 Report의 식별자이다. */
    @Column(name = "report_id")
    private Long reportId;

    /** 연결된 Area의 식별자이다. */
    @Column(name = "area_id")
    private Long areaId;
}
