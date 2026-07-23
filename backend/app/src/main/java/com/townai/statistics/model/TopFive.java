package com.townai.statistics.model;

import java.util.List;

/**
 * 다섯 평가 항목의 Area별 평균 Top 5이다.
 *
 * <p>각 목록은 점수 내림차순, 동점이면 Area ID 오름차순이며 최대 5건이다.</p>
 *
 * @param atmosphere 분위기 평균 상위 Area
 * @param infra 생활 인프라 평균 상위 Area
 * @param clean 청결도 평균 상위 Area
 * @param size 넓은 집 가능성 평균 상위 Area
 * @param access 접근성 평균 상위 Area
 */
public record TopFive(
        List<TopArea> atmosphere,
        List<TopArea> infra,
        List<TopArea> clean,
        List<TopArea> size,
        List<TopArea> access
) {
}
