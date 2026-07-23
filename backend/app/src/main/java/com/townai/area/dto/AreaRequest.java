package com.townai.area.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Area 생성과 전체 수정(PUT)에 공통으로 사용하는 요청이다.
 *
 * @param name 사용자가 구분할 동네 이름
 * @param prefecture 도도부현 이름
 * @param city 시구정촌 이름
 * @param station 선택적인 인접 역 이름
 */
public record AreaRequest(
        @NotBlank(message = "동네 이름은 필수입니다.")
        @Size(max = 25, message = "동네 이름은 최대 25자까지 입력할 수 있습니다.")
        String name,

        @NotBlank(message = "도 및 현 이름은 필수입니다.")
        @Size(max = 20, message = "도 및 현 이름은 최대 20자까지 입력할 수 있습니다.")
        String prefecture,

        @NotBlank(message = "마을 및 도시 이름은 필수입니다.")
        @Size(max = 20, message = "마을 및 도시 이름은 최대 20자까지 입력할 수 있습니다.")
        String city,

        @Size(max = 50, message = "가장 가까운 역 이름은 최대 50자까지 입력할 수 있습니다.")
        String station
) {
}
