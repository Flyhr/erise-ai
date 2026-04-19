<template>
  <div class="page-shell acceptance-page">
    <AppPageHeader
      title="前端闭环与验收"
      eyebrow="Release Readiness"
      subtitle="集中查看工作台、项目、文档、文件、搜索、AI 与管理后台的关键验收入口，并按统一清单完成发布演练。"
    >
      <template #actions>
        <el-button @click="router.push('/workspace')">返回工作台</el-button>
        <el-button type="primary" @click="openEntry(acceptancePaths[0])">开始验收</el-button>
      </template>
    </AppPageHeader>

    <section class="grid-4">
      <article v-for="metric in headlineMetrics" :key="metric.label" class="app-stat-card acceptance-stat">
        <span class="app-stat-card__label">{{ metric.label }}</span>
        <strong class="app-stat-card__value">{{ metric.value }}</strong>
        <p class="app-stat-card__hint">{{ metric.hint }}</p>
      </article>
    </section>

    <AppSectionCard
      title="关键路径验收"
      description="按入口逐项验证页面可达、状态可见、操作可追踪。项目详情与项目级 AI 均可从项目列表进入。"
      :unpadded="true"
    >
      <AppDataTable :data="acceptancePaths" stripe>
        <el-table-column label="模块" min-width="130">
          <template #default="{ row }">
            <div class="acceptance-module">
              <strong>{{ row.module }}</strong>
              <span>{{ row.entry }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="验收重点" min-width="320" show-overflow-tooltip>
          <template #default="{ row }">{{ row.focus }}</template>
        </el-table-column>
        <el-table-column label="验收角色" min-width="120">
          <template #default="{ row }">
            <AppStatusTag :label="row.audience" :tone="row.audienceTone" />
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <AppStatusTag :label="row.statusLabel" :tone="row.statusTone" />
          </template>
        </el-table-column>
        <el-table-column label="入口操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button text @click="openEntry(row)">打开</el-button>
          </template>
        </el-table-column>
      </AppDataTable>
    </AppSectionCard>

    <div class="grid-2">
      <AppSectionCard title="本轮前端闭环项" description="本次补齐的核心能力会直接影响 AI 闭环和整体验收效率。">
        <div class="acceptance-list">
          <div v-for="item in deliveredItems" :key="item.title" class="acceptance-list__item">
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
          </div>
        </div>
      </AppSectionCard>

      <AppSectionCard title="发布演练清单" description="建议按顺序演练，确保配置、权限、索引和告警链路都可追踪。">
        <div class="acceptance-list">
          <div v-for="item in releaseChecklist" :key="item.title" class="acceptance-list__item">
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
          </div>
        </div>
      </AppSectionCard>
    </div>

    <AppSectionCard title="建议验收顺序" description="优先从用户高频路径开始，再补管理员能力和发布演练。">
      <div class="acceptance-sequence">
        <div v-for="(item, index) in acceptanceSequence" :key="item.title" class="acceptance-sequence__item">
          <div class="acceptance-sequence__index">{{ index + 1 }}</div>
          <div class="acceptance-sequence__copy">
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
          </div>
        </div>
      </div>
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'

type Tone = 'primary' | 'success' | 'warning' | 'danger' | 'info'

interface AcceptancePathItem {
  module: string
  entry: string
  route: string
  focus: string
  audience: string
  audienceTone: Tone
  statusLabel: string
  statusTone: Tone
}

const router = useRouter()

const acceptancePaths: AcceptancePathItem[] = [
  {
    module: '工作台',
    entry: '/workspace',
    route: '/workspace',
    focus: '最近项目、最近文档/文件、AI 快捷入口、空态与加载态',
    audience: '普通用户',
    audienceTone: 'info',
    statusLabel: '可验收',
    statusTone: 'success',
  },
  {
    module: '项目',
    entry: '/projects -> 项目详情 -> 项目 AI',
    route: '/projects',
    focus: '项目概览、资料列表、项目级 AI 入口、文件/文档/表格跳转',
    audience: '普通用户',
    audienceTone: 'info',
    statusLabel: '可验收',
    statusTone: 'success',
  },
  {
    module: '文档',
    entry: '/documents',
    route: '/documents',
    focus: '筛选、创建、编辑、预览、删除确认、项目归属',
    audience: '普通用户',
    audienceTone: 'info',
    statusLabel: '可验收',
    statusTone: 'success',
  },
  {
    module: '文件',
    entry: '/files',
    route: '/files',
    focus: '上传、预览、在线编辑、知识解析状态、重试与删除',
    audience: '普通用户',
    audienceTone: 'info',
    statusLabel: '可验收',
    statusTone: 'success',
  },
  {
    module: '搜索',
    entry: '/search',
    route: '/search',
    focus: '项目过滤、结果分组、文档/文件跳转、知识状态展示',
    audience: '普通用户',
    audienceTone: 'info',
    statusLabel: '可验收',
    statusTone: 'success',
  },
  {
    module: 'AI',
    entry: '/ai',
    route: '/ai',
    focus: '会话元信息、模型与 Provider 状态、引用展示、索引状态、确认弹层',
    audience: '普通用户',
    audienceTone: 'info',
    statusLabel: '已补齐',
    statusTone: 'success',
  },
  {
    module: '管理后台',
    entry: '/admin',
    route: '/admin',
    focus: '管理员入口、模型配置、索引任务、审计入口、前端验收页',
    audience: '管理员',
    audienceTone: 'warning',
    statusLabel: '可验收',
    statusTone: 'success',
  },
]

const deliveredItems = [
  {
    title: 'AI 会话元信息',
    description: '在 AI 页增加会话、项目、消息数、最近活动等元信息卡片，减少切换排查成本。',
  },
  {
    title: '模型 / Provider 状态',
    description: '补齐模型可用状态、Provider 标识、输出模式与上下文窗口信息，并在消息头展示本轮模型元数据。',
  },
  {
    title: '引用与索引状态',
    description: '增强引用卡片摘要展示，并把临时文件解析 / 索引状态、失败重试与移除操作集中到同一区域。',
  },
  {
    title: '确认弹层与管理员入口',
    description: '为新建对话、停止生成、删除会话补上明确确认，并在工作台导航里为管理员提供后台入口。',
  },
]

const releaseChecklist = [
  {
    title: '配置核对',
    description: '确认 `VITE_API_BASE_URL`、模型提供方配置、索引任务依赖和 MCP / n8n 环境变量已经按目标环境注入。',
  },
  {
    title: '权限演练',
    description: '用普通用户验证工作台、搜索、AI 可访问；用管理员验证后台入口、模型页、索引任务页与验收页可访问。',
  },
  {
    title: '数据链路巡检',
    description: '至少上传一个文件、创建一篇文档、跑一轮 AI 对话，确认索引状态、引用跳转和失败提示都可追踪。',
  },
  {
    title: '外围流程联调',
    description: '抽样验证审批、通知、巡检和告警链路，确保 n8n 仍然只承担外围工作流，不接管核心同步问答。',
  },
]

const acceptanceSequence = [
  {
    title: '工作台与项目入口',
    description: '先验证登录后落地页、项目列表和项目详情是否流畅，再检查项目到文件、文档与 AI 的跳转。',
  },
  {
    title: '文档、文件、搜索',
    description: '围绕上传、编辑、预览、筛选与删除确认做一轮高频操作回归。',
  },
  {
    title: 'AI 会话闭环',
    description: '验证模型选择、引用展示、临时文件索引状态、会话删除与停止生成确认弹层。',
  },
  {
    title: '管理员与发布演练',
    description: '最后检查后台入口、索引任务、模型配置和本页清单，准备上线演练与回滚信息。',
  },
]

const headlineMetrics = computed(() => [
  {
    label: '关键验收模块',
    value: `${acceptancePaths.length}`,
    hint: '工作台、项目、文档、文件、搜索、AI 与后台全部纳入同一页验收。',
  },
  {
    label: '本轮补齐项',
    value: `${deliveredItems.length}`,
    hint: '围绕 AI 闭环、状态可见性、确认流程和管理员入口完成收口。',
  },
  {
    label: '发布演练步骤',
    value: `${releaseChecklist.length}`,
    hint: '从配置、权限、数据链路到外围流程联调统一收敛。',
  },
  {
    label: '建议顺序',
    value: `${acceptanceSequence.length}`,
    hint: '优先用户高频路径，再做 AI 深水区和管理员发布演练。',
  },
])

const openEntry = async (item: AcceptancePathItem) => {
  await router.push(item.route)
}
</script>

<style scoped>
.acceptance-page {
  gap: 22px;
}

.acceptance-stat {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.acceptance-module {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.acceptance-module strong {
  color: #101828;
  font-size: 15px;
  font-weight: 700;
}

.acceptance-module span {
  color: #667085;
  font-size: 12px;
}

.acceptance-list,
.acceptance-sequence {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.acceptance-list__item,
.acceptance-sequence__item {
  display: flex;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid var(--line);
  background: var(--surface-strong);
}

.acceptance-list__item {
  flex-direction: column;
}

.acceptance-list__item strong,
.acceptance-sequence__copy strong {
  color: #101828;
  font-size: 15px;
  font-weight: 700;
}

.acceptance-list__item p,
.acceptance-sequence__copy p {
  margin: 0;
  color: #667085;
  line-height: 1.7;
}

.acceptance-sequence__index {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.12);
  color: var(--accent);
  font-weight: 800;
  flex-shrink: 0;
}

.acceptance-sequence__copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
