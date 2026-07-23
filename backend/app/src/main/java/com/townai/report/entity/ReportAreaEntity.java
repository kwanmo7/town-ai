package com.townai.report.entity;

import com.townai.area.entity.AreaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Report 생성 당시 분석 대상 Area와 입력 표시 순서를 보존하는 연결 Entity이다.
 *
 * <p>Visit 저장 시점과는 무관하며 Report가 생성될 때만 Row가 만들어진다.
 * Area 이름이나 Visit이 나중에 바뀌어도 어떤 Area를 어떤 순서로 분석했는지는
 * 이 연결을 통해 조회할 수 있다.</p>
 */
@Entity
@Table(name = "report_area")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportAreaEntity {

    @EmbeddedId
    private ReportAreaId id;

    @MapsId("reportId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private ReportEntity report;

    @MapsId("areaId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private AreaEntity area;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * 저장된 Report와 Area로 복합 식별자를 구성한다.
     *
     * @param report 대상 Report. ID가 할당된 상태여야 함
     * @param area 생성 당시 분석 대상 Area. ID가 할당된 상태여야 함
     * @param displayOrder 요청과 Prompt에서 사용한 1부터 시작하는 순서
     */
    public ReportAreaEntity(ReportEntity report, AreaEntity area, int displayOrder) {
        this.report = report;
        this.area = area;
        this.displayOrder = displayOrder;
        this.id = new ReportAreaId(report.getId(), area.getId());
    }
}
