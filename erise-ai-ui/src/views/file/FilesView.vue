<template>
  <div class="page-shell">
    <!-- <AppPageHeader :title="pageTitle" eyebrow="文件中心" :subtitle="pageSubtitle" :show-back="Boolean(scopedProjectId)"
      :back-to="scopedProjectId ? `/projects/${scopedProjectId}` : undefined" back-label="返回项目概览">
      <template #actions>
        <el-upload :show-file-list="false" :before-upload="beforeUpload" :disabled="!uploadProjectId">
          <el-button type="primary" :disabled="!uploadProjectId">{{ uploadButtonLabel }}</el-button>
        </el-upload>
      </template>
</AppPageHeader> -->

    <ProjectSubnav v-if="scopedProjectId" :project-id="scopedProjectId" />

    <AppFilterBar>
      <el-input v-model="keyword" style="grid-column: span 5" clearable placeholder="按文件名搜索"
        @keyup.enter="handleSearch" />
      <el-select v-model="selectedProjectId" style="grid-column: span 4" filterable clearable
        :disabled="Boolean(scopedProjectId)" placeholder="筛选项目">
        <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
      </el-select>
      <div style="grid-column: span 3; display: flex; align-items: center; color: var(--muted); font-size: 13px;">
        {{ scopedProjectId ? '当前仅显示该项目下的文件。' : '支持按项目筛选后上传和管理文件。' }}
      </div>
      <template #actions>
        <el-button @click="resetFilters">重置</el-button>
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </template>
    </AppFilterBar>

    <AppSectionCard title="文件列表" :unpadded="true">
      <AppDataTable :data="files" stripe>
        <el-table-column label="名称" min-width="260">
          <template #default="{ row }">
            <div class="file-name-cell">
              <strong>{{ row.fileName }}</strong>
              <span>{{ uploadStatusLabel(row.uploadStatus) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <AppStatusTag :label="normalizeFileTypeLabel(row.fileExt, row.mimeType)" tone="info" />
          </template>
        </el-table-column>
        <el-table-column label="所属项目" min-width="180">
          <template #default="{ row }">{{ projectLabel(row.projectId) }}</template>
        </el-table-column>
        <el-table-column label="大小" width="120">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="上传时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="220" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text @click="$router.push(`/files/${row.id}`)">详情</el-button>
              <el-button text @click="preview(row)">预览</el-button>
              <el-dropdown>
                <el-button text>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="isOfficeEditableFile(row.fileExt)"
                      @click="$router.push(`/files/${row.id}/edit`)">
                      在线编辑
                    </el-dropdown-item>
                    <el-dropdown-item @click="removeFileItem(row.id, row.fileName)">
                      删除文件
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </AppDataTable>

      <template #footer>
        <div class="files-footer">
          <span class="page-subtitle" style="margin: 0;">共 {{ total }} 个文件</span>
          <el-pagination background layout="prev, pager, next" :current-page="pageNum" :page-size="pageSize"
            :total="total" @current-change="handlePageChange" />
        </div>
      </template>
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProject } from '@/api/project'
import { completeUpload, deleteFile, getFiles, initUpload, previewFileBinary, previewOfficeFile, uploadFileBinary } from '@/api/file'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppFilterBar from '@/components/common/AppFilterBar.vue'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import ProjectSubnav from '@/components/common/ProjectSubnav.vue'
import { useProjectDirectory } from '@/composables/useProjectDirectory'
import type { FileView } from '@/types/models'
import { formatDateTime, formatFileSize, isOfficeEditableFile, normalizeFileTypeLabel, resolveErrorMessage, uploadStatusLabel } from '@/utils/formatters'

const props = defineProps<{ id?: string }>()
const scopedProjectId = props.id ? Number(props.id) : undefined
const { projects, loadProjects, projectLabel } = useProjectDirectory()
const projectName = ref('')
const files = ref<FileView[]>([])
const keyword = ref('')
const selectedProjectId = ref<number | undefined>(scopedProjectId)
const pageNum = ref(1)
const pageSize = ref(12)
const total = ref(0)

const uploadProjectId = computed(() => scopedProjectId || selectedProjectId.value)
// const pageTitle = computed(() => (scopedProjectId ? `${projectName.value || '当前项目'} / 文件` : '全部文件'))
// const pageSubtitle = computed(() =>
//   scopedProjectId
//     ? '项目文件页与全局文件中心共用同一套列表规则，支持直接上传、预览与编辑。'
//     : '集中浏览所有文件，支持按项目筛选、搜索和上传。',
// )
const uploadButtonLabel = computed(() => (uploadProjectId.value ? '上传文件' : '请先选择项目'))

const load = async () => {
  const page = await getFiles({ projectId: scopedProjectId || selectedProjectId.value, q: keyword.value.trim() || undefined, pageNum: pageNum.value, pageSize: pageSize.value })
  files.value = page.records
  total.value = page.total
}

const handleSearch = async () => {
  pageNum.value = 1
  await load()
}

const resetFilters = async () => {
  keyword.value = ''
  selectedProjectId.value = scopedProjectId
  pageNum.value = 1
  await load()
}

const handlePageChange = async (value: number) => {
  pageNum.value = value
  await load()
}

const beforeUpload = async (rawFile: File) => {
  if (!uploadProjectId.value) {
    ElMessage.warning('请先选择一个项目，再上传文件。')
    return false
  }
  let init: { fileId: number } | undefined
  try {
    init = await initUpload({
      projectId: uploadProjectId.value,
      fileName: rawFile.name,
      fileSize: rawFile.size,
      mimeType: rawFile.type || 'application/octet-stream',
    })
    await uploadFileBinary(init.fileId, rawFile)
    await completeUpload(init.fileId)
    ElMessage.success('文件上传成功')
    await load()
  } catch (error) {
    if (init?.fileId) {
      try {
        await deleteFile(init.fileId)
      } catch {
        // best effort cleanup only
      }
    }
    ElMessage.error(resolveErrorMessage(error, '文件上传失败，请稍后重试'))
    await load()
  }
  return false
}

const preview = async (file: FileView) => {
  try {
    if (isOfficeEditableFile(file.fileExt)) {
      await previewOfficeFile(file.id)
      return
    }
    await previewFileBinary(file.id)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '文件预览失败，请稍后重试'))
  }
}

const removeFileItem = async (id: number, fileName: string) => {
  await ElMessageBox.confirm(`确认删除文件“${fileName}”吗？`, '删除文件', { type: 'warning' })
  await deleteFile(id)
  ElMessage.success('文件已删除')
  await load()
}

onMounted(async () => {
  await loadProjects()
  if (scopedProjectId) {
    const project = await getProject(scopedProjectId)
    projectName.value = project.name
  }
  await load()
})
</script>

<style scoped>
.file-name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.file-name-cell strong {
  font-size: 15px;
  font-weight: 700;
}

.file-name-cell span {
  color: var(--muted);
  font-size: 12px;
}

.files-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
</style>
