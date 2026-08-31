package com.townai.report.storage;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Production용 Google Cloud Storage Client를 구성한다.
 *
 * <p>명시적인 JSON Key를 읽지 않고 Google Cloud Client Library의
 * Application Default Credentials를 사용한다. 따라서 Cloud Run에서는 연결된
 * Service Account 권한으로 인증한다.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "town-ai.report-storage",
        name = "type",
        havingValue = "gcs"
)
public class GcsStorageConfiguration {

    /**
     * Application Default Credentials를 사용하는 Storage Client를 생성한다.
     *
     * @return 환경에서 Project와 인증정보를 찾는 Google Cloud Storage Client
     */
    @Bean
    @ConditionalOnMissingBean(Storage.class)
    public Storage googleCloudStorage() {
        return StorageOptions.getDefaultInstance().getService();
    }
}
