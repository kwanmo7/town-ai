package com.townai.report.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GcsReportStorageTest {

    private static final String BUCKET_NAME =
            "town-ai-reports-test";
    private static final String STORAGE_PATH =
            "reports/v1/area/센터미나미_2026-07-25_10.md";

    private final Storage storage = mock(Storage.class);
    private final GcsReportStorage reportStorage =
            new GcsReportStorage(storage, BUCKET_NAME);

    @Test
    void writesUtfEightMarkdownWithContentType() {
        String markdown = "# 지역 리포트\n한글 본문";

        reportStorage.write(STORAGE_PATH, markdown);

        ArgumentCaptor<BlobInfo> blobInfoCaptor =
                ArgumentCaptor.forClass(BlobInfo.class);
        ArgumentCaptor<byte[]> contentCaptor =
                ArgumentCaptor.forClass(byte[].class);
        verify(storage).create(
                blobInfoCaptor.capture(),
                contentCaptor.capture()
        );
        BlobInfo blobInfo = blobInfoCaptor.getValue();
        assertEquals(BUCKET_NAME, blobInfo.getBucket());
        assertEquals(STORAGE_PATH, blobInfo.getName());
        assertEquals(
                "text/markdown; charset=UTF-8",
                blobInfo.getContentType()
        );
        assertArrayEquals(
                markdown.getBytes(StandardCharsets.UTF_8),
                contentCaptor.getValue()
        );
    }

    @Test
    void readsObjectAsUtfEightMarkdown() {
        String markdown = "# 지역 리포트\n한글 본문";
        BlobId blobId = BlobId.of(BUCKET_NAME, STORAGE_PATH);
        when(storage.readAllBytes(blobId)).thenReturn(
                markdown.getBytes(StandardCharsets.UTF_8)
        );

        String result = reportStorage.read(STORAGE_PATH);

        assertEquals(markdown, result);
    }

    @Test
    void treatsMissingObjectDeleteAsSuccess() {
        BlobId blobId = BlobId.of(BUCKET_NAME, STORAGE_PATH);
        when(storage.delete(blobId)).thenReturn(false);

        reportStorage.delete(STORAGE_PATH);

        verify(storage).delete(blobId);
    }

    @Test
    void convertsGoogleCloudFailureToStorageException() {
        when(storage.readAllBytes(any(BlobId.class))).thenThrow(
                new StorageException(503, "unavailable")
        );

        assertThrows(
                ReportStorageException.class,
                () -> reportStorage.read(STORAGE_PATH)
        );
    }

    @Test
    void rejectsPathOutsideReportsPrefix() {
        assertThrows(
                ReportStorageException.class,
                () -> reportStorage.delete("other/private-object.md")
        );
        assertThrows(
                ReportStorageException.class,
                () -> reportStorage.delete("reports/../private-object.md")
        );
        assertThrows(
                ReportStorageException.class,
                () -> reportStorage.delete("reports//private-object.md")
        );
        verify(storage, org.mockito.Mockito.never())
                .delete(any(BlobId.class));
    }

    @Test
    void requiresBucketAndNonNullContent() {
        assertThrows(
                IllegalStateException.class,
                () -> new GcsReportStorage(storage, " ")
        );
        assertThrows(
                ReportStorageException.class,
                () -> reportStorage.write(STORAGE_PATH, null)
        );
        verifyNoInteractions(storage);
    }
}
