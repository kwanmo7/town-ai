package com.townai.line.persistence;

import com.townai.line.entity.LineWebhookEventEntity;
import com.townai.line.model.LineWebhookEventPayload;
import com.townai.line.repository.LineWebhookEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 검증된 LINE 이벤트를 하나의 DB Transaction으로 저장한다.
 *
 * <p>이미 저장된 {@code webhookEventId}는 새 Row를 만들지 않는다. 반환 목록에는
 * 기존 이벤트도 포함해 Webhook 재전송 시 Dispatcher 전달을 다시 시도할 수 있게
 * 한다. Transaction이 Commit된 뒤에만 상위 Service가 Dispatcher를 호출한다.</p>
 */
@Service
public class LineWebhookEventPersistenceService {

    private final LineWebhookEventRepository eventRepository;

    /**
     * LINE 이벤트 영속화 Service를 생성한다.
     *
     * @param eventRepository LINE Webhook 이벤트 Repository
     */
    public LineWebhookEventPersistenceService(
            LineWebhookEventRepository eventRepository
    ) {
        this.eventRepository = eventRepository;
    }

    /**
     * 요청 순서를 유지하면서 아직 존재하지 않는 이벤트만 저장한다.
     *
     * @param payloads 검증을 통과한 이벤트 Payload 목록
     * @return 저장 여부와 관계없이 Dispatcher에 전달할 고유 이벤트 ID 목록
     */
    @Transactional
    public List<String> store(List<LineWebhookEventPayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, LineWebhookEventPayload> uniquePayloads =
                payloads.stream().collect(Collectors.toMap(
                        LineWebhookEventPayload::webhookEventId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        Set<String> existingIds = eventRepository
                .findAllById(uniquePayloads.keySet())
                .stream()
                .map(LineWebhookEventEntity::getWebhookEventId)
                .collect(Collectors.toCollection(HashSet::new));

        List<LineWebhookEventEntity> newEvents = uniquePayloads.values()
                .stream()
                .filter(payload -> !existingIds.contains(
                        payload.webhookEventId()
                ))
                .map(LineWebhookEventEntity::received)
                .toList();

        if (!newEvents.isEmpty()) {
            eventRepository.saveAllAndFlush(newEvents);
        }
        return List.copyOf(uniquePayloads.keySet());
    }
}
