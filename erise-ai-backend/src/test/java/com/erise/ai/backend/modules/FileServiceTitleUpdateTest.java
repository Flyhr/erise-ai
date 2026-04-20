package com.erise.ai.backend.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FileServiceTitleUpdateTest {

    private final FileMapper fileMapper = mock(FileMapper.class);
    private final FileParseTaskMapper fileParseTaskMapper = mock(FileParseTaskMapper.class);
    private final TagMapper tagMapper = mock(TagMapper.class);
    private final FileTagRelMapper fileTagRelMapper = mock(FileTagRelMapper.class);
    private final FileEditContentMapper fileEditContentMapper = mock(FileEditContentMapper.class);
    private final ProjectService projectService = mock(ProjectService.class);
    private final com.erise.ai.backend.integration.storage.MinioStorageClient storageClient =
            mock(com.erise.ai.backend.integration.storage.MinioStorageClient.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final RagKnowledgeService ragKnowledgeService = mock(RagKnowledgeService.class);
    private final StoredTextExtractionSupport storedTextExtractionSupport = mock(StoredTextExtractionSupport.class);
    private final FileParseStatusSupport fileParseStatusSupport = mock(FileParseStatusSupport.class);
    private final FileIndexPipelineService fileIndexPipelineService = mock(FileIndexPipelineService.class);

    private final FileService fileService = new FileService(
            fileMapper,
            fileParseTaskMapper,
            tagMapper,
            fileTagRelMapper,
            fileEditContentMapper,
            projectService,
            storageClient,
            auditLogService,
            ragKnowledgeService,
            storedTextExtractionSupport,
            fileParseStatusSupport,
            fileIndexPipelineService
    );

    @Test
    void internalUpdateTitleKeepsExistingExtensionAndUpdatesRagTitle() {
        FileEntity entity = buildFileEntity("txt_sample_file_5MB.txt", "txt");
        FileEditContentEntity storedContent = new FileEditContentEntity();
        storedContent.setPlainText("正文内容");

        when(fileMapper.selectById(501L)).thenReturn(entity);
        when(fileEditContentMapper.selectOne(any())).thenReturn(storedContent);
        when(projectService.requireAccessibleProject(88L, 9L)).thenReturn(new ProjectEntity());
        when(fileParseStatusSupport.resolve(anyLong(), any(), any()))
                .thenReturn(new FileParseStatusView("READY", "READY", null));

        InternalFileContextView detail = fileService.internalUpdateTitle(501L, 9L, "测试文件5mb");

        assertEquals("测试文件5mb.txt", entity.getFileName());
        assertEquals("测试文件5mb.txt", detail.fileName());
        verify(fileMapper).updateById(entity);
        verify(ragKnowledgeService).updateKbSourceTitle(9L, 88L, "FILE", 501L, "测试文件5mb.txt", 9L);
    }

    @Test
    void internalUpdateTitleDoesNotDuplicateSuffixWhenUserIncludesExtension() {
        FileEntity entity = buildFileEntity("old-name.txt", "txt");
        FileEditContentEntity storedContent = new FileEditContentEntity();
        storedContent.setPlainText("正文内容");

        when(fileMapper.selectById(501L)).thenReturn(entity);
        when(fileEditContentMapper.selectOne(any())).thenReturn(storedContent);
        when(projectService.requireAccessibleProject(88L, 9L)).thenReturn(new ProjectEntity());
        when(fileParseStatusSupport.resolve(anyLong(), any(), any()))
                .thenReturn(new FileParseStatusView("READY", "READY", null));

        InternalFileContextView detail = fileService.internalUpdateTitle(501L, 9L, "测试文件5mb.txt");

        assertEquals("测试文件5mb.txt", entity.getFileName());
        assertEquals("测试文件5mb.txt", detail.fileName());
        verify(ragKnowledgeService).updateKbSourceTitle(9L, 88L, "FILE", 501L, "测试文件5mb.txt", 9L);
        verify(auditLogService).log(eq(9L), eq("FILE_TITLE_UPDATE_BY_AI"), eq("FILE"), eq(501L), any());
    }

    private FileEntity buildFileEntity(String fileName, String fileExt) {
        FileEntity entity = new FileEntity();
        entity.setId(501L);
        entity.setOwnerUserId(9L);
        entity.setProjectId(88L);
        entity.setFileName(fileName);
        entity.setFileExt(fileExt);
        entity.setMimeType("text/plain");
        entity.setStorageKey("projects/88/test-file.txt");
        entity.setParseStatus("READY");
        entity.setIndexStatus("READY");
        entity.setArchived(0);
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }
}
