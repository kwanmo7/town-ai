package com.townai.line.model;

import java.util.List;

/**
 * COMPARE Report 대상 Area의 선택 상태를 전환하는 명령이다.
 *
 * @param areaId 선택 상태를 전환할 Area ID
 * @param selectedAreaIds 버튼을 누르기 전 선택된 Area ID 목록
 */
public record LineCompareToggleCommand(
        long areaId,
        List<Long> selectedAreaIds
) implements LinePostbackCommand {

    /**
     * 선택 목록을 불변으로 보존한다.
     */
    public LineCompareToggleCommand {
        selectedAreaIds = List.copyOf(selectedAreaIds);
    }
}
