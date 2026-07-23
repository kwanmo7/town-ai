package com.townai.visit.service.impl;

import com.townai.area.repository.AreaRepository;
import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.visit.dto.VisitDraftRequest;
import com.townai.visit.dto.VisitDraftResponse;
import com.townai.visit.parser.VisitParserAiClient;
import com.townai.visit.parser.VisitParserInput;
import com.townai.visit.parser.validation.InvalidVisitDraftOutputException;
import com.townai.visit.parser.validation.VisitDraftOutputValidator;
import com.townai.visit.service.VisitDraftService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 자연어 Visit 초안 생성 Use Case를 조정한다.
 *
 * <p>현재 날짜와 활성 Area 목록으로 Parser 입력을 만들고, AI 원본 출력을 별도
 * Validator에 전달한다. 출력 계약 위반 시 검증 사유를 포함해 정확히 한 번 교정
 * 요청하며, 두 번째 출력도 실패하면 외부 AI 오류로 변환한다. 이 과정에서는
 * Visit Repository를 사용하지 않으므로 어떤 초안도 저장되지 않는다.</p>
 */
@Service
@Transactional(readOnly = true)
public class VisitDraftServiceImpl implements VisitDraftService {

    private static final Logger log =
            LoggerFactory.getLogger(VisitDraftServiceImpl.class);

    private final AreaRepository areaRepository;
    private final VisitParserAiClient parserAiClient;
    private final VisitDraftOutputValidator outputValidator;
    private final Clock clock;
    private final ZoneId userTimeZone;

    /**
     * 자연어 Visit 초안 생성 Service를 구성한다.
     *
     * @param areaRepository Parser 후보로 제공할 활성 Area Repository
     * @param parserAiClient 자연어 Parser 모델 호출 Port
     * @param outputValidator AI 원본 JSON 검증기
     * @param clock 현재 시각 기준
     * @param userTimeZone 상대 날짜를 해석할 사용자 생활권 시간대
     */
    public VisitDraftServiceImpl(
            AreaRepository areaRepository,
            VisitParserAiClient parserAiClient,
            VisitDraftOutputValidator outputValidator,
            Clock clock,
            ZoneId userTimeZone
    ) {
        this.areaRepository = areaRepository;
        this.parserAiClient = parserAiClient;
        this.outputValidator = outputValidator;
        this.clock = clock;
        this.userTimeZone = userTimeZone;
    }

    @Override
    public VisitDraftResponse create(VisitDraftRequest request) {
        VisitParserInput input = createInput(request.text().strip());
        try {
            return outputValidator.validate(
                    parserAiClient.parse(input, null),
                    input
            );
        } catch (InvalidVisitDraftOutputException firstFailure) {
            log.warn(
                    "Visit Parser output validation failed. Retrying correction once. reason={}",
                    firstFailure.getMessage()
            );
            String correctedOutput = parserAiClient.parse(
                    input,
                    firstFailure.getMessage()
            );
            try {
                return outputValidator.validate(correctedOutput, input);
            } catch (InvalidVisitDraftOutputException secondFailure) {
                log.error(
                        "Corrected Visit Parser output validation failed. reason={}",
                        secondFailure.getMessage()
                );
                throw new ApiException(ErrorCode.OPENAI_API_ERROR);
            }
        }
    }

    /**
     * Parser가 임의의 Area를 만들지 못하도록 현재 활성 Area만 입력 후보로 제공한다.
     */
    private VisitParserInput createInput(String text) {
        List<VisitParserInput.AreaInput> areas =
                areaRepository.findAllByDeletedAtIsNullOrderByIdAsc()
                        .stream()
                        .map(area -> new VisitParserInput.AreaInput(
                                area.getId(),
                                area.getName(),
                                area.getPrefecture(),
                                area.getCity(),
                                area.getStation()
                        ))
                        .toList();
        return new VisitParserInput(
                LocalDate.ofInstant(clock.instant(), userTimeZone),
                text,
                areas
        );
    }
}
