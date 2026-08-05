package com.townai.visit.parser.validation;

import com.townai.visit.dto.VisitDraftAreaResponse;
import com.townai.visit.dto.VisitDraftResponse;
import com.townai.visit.parser.VisitParserInput;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VisitDraftOutputValidatorTest {

    private final VisitDraftOutputValidator validator =
            new VisitDraftOutputValidator(
                    JsonMapper.builder().findAndAddModules().build()
            );

    @Test
    void doesNotGuessAreaFromSubstringWhenParserReturnsNull() {
        VisitDraftResponse result = validator.validate("""
                {
                  "area": null,
                  "visitDate": "2026-08-03",
                  "atmosphereScore": 8,
                  "infraScore": 8,
                  "cleanScore": 8,
                  "sizeScore": 7,
                  "accessScore": 7,
                  "memo": "센터미나미가 아니라 다른 지역을 방문함",
                  "warnings": ["지역을 확정할 수 없습니다."]
                }
                """, input("센터미나미가 아니라 다른 지역을 방문했어"));

        assertNull(result.area());
    }

    @Test
    void doesNotReuseSameNameAreaAtDifferentLocation() {
        VisitDraftResponse result = validator.validate("""
                {
                  "area": {
                    "id": null,
                    "name": "센터미나미",
                    "prefecture": "도쿄도",
                    "city": "미나토구",
                    "station": null
                  },
                  "visitDate": "2026-08-03",
                  "atmosphereScore": 8,
                  "infraScore": 8,
                  "cleanScore": 8,
                  "sizeScore": 7,
                  "accessScore": 7,
                  "memo": null,
                  "warnings": ["새 지역의 위치를 확인해주세요."]
                }
                """, input("도쿄도 미나토구 센터미나미 방문"));

        assertNull(result.area().id());
        assertEquals("도쿄도", result.area().prefecture());
        assertEquals("미나토구", result.area().city());
    }

    @Test
    void convertsLiteralNullAndGenericLocationPlaceholdersToNull() {
        VisitDraftResponse result = validator.validate("""
                {
                  "area": {
                    "id": null,
                    "name": "센터미나미",
                    "prefecture": "광역권",
                    "city": "null",
                    "station": "unknown"
                  },
                  "visitDate": "2026-08-03",
                  "atmosphereScore": 8,
                  "infraScore": 8,
                  "cleanScore": 8,
                  "sizeScore": 7,
                  "accessScore": 7,
                  "memo": null,
                  "warnings": ["위치 정보를 확인해주세요."]
                }
                """, input("센터미나미 방문"));

        assertNull(result.area().prefecture());
        assertNull(result.area().city());
        assertNull(result.area().station());
    }

    @Test
    void mergesOnlyFieldsDeclaredByRevisionOutput() {
        VisitDraftResponse existing = new VisitDraftResponse(
                new VisitDraftAreaResponse(
                        1L,
                        "센터미나미",
                        "가나가와현",
                        "요코하마시",
                        "센터미나미역"
                ),
                LocalDate.parse("2026-08-03"),
                8,
                8,
                8,
                7,
                7,
                "기존 메모",
                List.of()
        );
        VisitParserInput input = new VisitParserInput(
                LocalDate.parse("2026-08-04"),
                "접근성만 9로 수정",
                input("").areas(),
                VisitParserInput.ExistingDraftInput.from(existing)
        );

        VisitDraftResponse result = validator.validateRevision("""
                {
                  "area": {
                    "id": 1,
                    "name": "센터미나미",
                    "prefecture": "가나가와현",
                    "city": "요코하마시",
                    "station": "센터미나미역"
                  },
                  "visitDate": "2026-08-03",
                  "atmosphereScore": 1,
                  "infraScore": 2,
                  "cleanScore": 3,
                  "sizeScore": 4,
                  "accessScore": 9,
                  "memo": "모델이 임의로 바꾼 메모",
                  "warnings": [],
                  "changedFields": ["accessScore"]
                }
                """, input);

        assertEquals(8, result.atmosphereScore());
        assertEquals(8, result.infraScore());
        assertEquals(8, result.cleanScore());
        assertEquals(7, result.sizeScore());
        assertEquals(9, result.accessScore());
        assertEquals("기존 메모", result.memo());
    }

    private VisitParserInput input(String text) {
        return new VisitParserInput(
                LocalDate.parse("2026-08-04"),
                text,
                List.of(new VisitParserInput.AreaInput(
                        1L,
                        "센터미나미",
                        "가나가와현",
                        "요코하마시",
                        "센터미나미역"
                ))
        );
    }
}
