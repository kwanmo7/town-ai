package com.townai.report.link;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

/**
 * Report ID, 접근 동작과 만료 시각을 HMAC-SHA256으로 서명하고 검증한다.
 *
 * <p>서명에는 접근 동작도 포함하므로 본문 링크를 다운로드 링크로 바꾸어 재사용할 수 없다.
 * URL-safe Base64를 사용해 별도 URL Encoding 없이 Query Parameter에 넣을 수 있다.</p>
 */
@Service
public class ReportSignedLinkService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String PUBLIC_PATH_PREFIX = "/api/public/reports/";

    private final ReportLinkProperties properties;
    private final Clock clock;

    /**
     * Report 서명 링크 Service를 생성한다.
     *
     * @param properties HMAC Secret과 링크 유효 기간
     * @param clock 만료 시각 계산과 검증에 사용할 UTC Clock
     */
    public ReportSignedLinkService(
            ReportLinkProperties properties,
            Clock clock
    ) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 지금부터 설정된 유효 기간 동안 사용할 공개 Report 경로를 생성한다.
     *
     * @param reportId Report ID
     * @param action 본문 조회 또는 다운로드
     * @return 만료 시각과 서명이 포함된 절대 경로
     * @throws IllegalStateException HMAC Secret 또는 유효 기간 설정이 올바르지 않은 경우
     */
    public String createPath(Long reportId, ReportLinkAction action) {
        ensureConfigured();
        long expiresAt = Instant.now(clock)
                .plus(properties.validity())
                .getEpochSecond();
        String signature = sign(reportId, action, expiresAt);
        return path(reportId, action)
                + "?expires=" + expiresAt
                + "&signature=" + signature;
    }

    /**
     * 공개 Report 요청의 만료 시각과 HMAC 서명을 검증한다.
     *
     * @param reportId Report ID
     * @param action 요청한 접근 동작
     * @param expiresAt URL에 포함된 Unix Epoch Seconds 만료 시각
     * @param signature URL-safe Base64 HMAC 서명
     * @throws ApiException 링크가 만료됐거나 서명이 일치하지 않는 경우
     */
    public void verify(
            Long reportId,
            ReportLinkAction action,
            long expiresAt,
            String signature
    ) {
        ensureConfigured();
        if (signature == null || signature.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REPORT_LINK);
        }

        byte[] expected = sign(reportId, action, expiresAt)
                .getBytes(StandardCharsets.US_ASCII);
        byte[] received = signature.getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, received)) {
            throw new ApiException(ErrorCode.INVALID_REPORT_LINK);
        }
        if (expiresAt <= Instant.now(clock).getEpochSecond()) {
            throw new ApiException(ErrorCode.REPORT_LINK_EXPIRED);
        }
    }

    private String sign(Long reportId, ReportLinkAction action, long expiresAt) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.signingSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            ));
            byte[] signature = mac.doFinal(
                    payload(reportId, action, expiresAt).getBytes(StandardCharsets.UTF_8)
            );
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Report link signature could not be created.", exception);
        }
    }

    private String payload(Long reportId, ReportLinkAction action, long expiresAt) {
        return "GET\n" + path(reportId, action) + "\n" + expiresAt;
    }

    private String path(Long reportId, ReportLinkAction action) {
        return PUBLIC_PATH_PREFIX + reportId + "/" + action.pathSegment();
    }

    /**
     * 서명 Secret과 링크 유효 기간이 안전한 최소 조건을 충족하는지 확인한다.
     *
     * @throws IllegalStateException Secret이 32자보다 짧거나 유효 기간이 양수가 아닌 경우
     */
    public void ensureConfigured() {
        if (properties.signingSecret() == null
                || properties.signingSecret().length() < 32) {
            throw new IllegalStateException(
                    "REPORT_LINK_SIGNING_SECRET must contain at least 32 characters."
            );
        }
        if (properties.validity() == null
                || properties.validity().isZero()
                || properties.validity().isNegative()) {
            throw new IllegalStateException(
                    "REPORT_LINK_VALIDITY must be a positive duration."
            );
        }
    }
}
