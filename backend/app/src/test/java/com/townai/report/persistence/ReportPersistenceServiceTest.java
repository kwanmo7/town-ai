package com.townai.report.persistence;

import com.townai.report.entity.ReportEntity;
import com.townai.report.entity.ReportType;
import com.townai.report.repository.ReportAreaRepository;
import com.townai.report.repository.ReportRepository;
import com.townai.report.storage.ReportStorage;
import com.townai.report.storage.ReportStoragePathFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportPersistenceServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportAreaRepository reportAreaRepository;

    @Mock
    private ReportStorage reportStorage;

    @Mock
    private ReportStoragePathFactory pathFactory;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private ReportPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        persistenceService = new ReportPersistenceService(
                reportRepository,
                reportAreaRepository,
                reportStorage,
                pathFactory,
                transactionManager
        );
    }

    @Test
    void deletesStoredObjectWhenDatabaseFailsAfterStorageWrite() {
        String storagePath = "reports/v1/summary/2026-07-24_10.md";
        when(pathFactory.create(ReportType.SUMMARY, 10L, List.of()))
                .thenReturn(storagePath);
        AtomicInteger saveCount = new AtomicInteger();
        when(reportRepository.saveAndFlush(any(ReportEntity.class)))
                .thenAnswer(invocation -> {
                    ReportEntity report = invocation.getArgument(0);
                    if (saveCount.getAndIncrement() == 0) {
                        ReflectionTestUtils.setField(report, "id", 10L);
                        return report;
                    }
                    throw new IllegalStateException("DB update failed");
                });

        assertThrows(
                IllegalStateException.class,
                () -> persistenceService.persist(
                        ReportType.SUMMARY,
                        "test-model",
                        "summary-v1",
                        "# report",
                        List.of()
                )
        );

        InOrder storageOrder = inOrder(reportStorage);
        storageOrder.verify(reportStorage).write(storagePath, "# report");
        storageOrder.verify(reportStorage).delete(storagePath);
        verify(transactionManager).rollback(transactionStatus);
    }
}
