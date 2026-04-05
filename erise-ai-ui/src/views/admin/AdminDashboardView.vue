<template>
  <div v-if="dashboard" class="page-shell section-stack admin-dashboard">
    <AppPageHeader
      title="平台运营与 AI 工作链路总览"
      eyebrow="后台总览"
      subtitle="后台只保留高密度运营信息、趋势图和关键审计表，不再使用营销式 hero。"
    >
      <template #actions>
        <el-button type="primary" @click="router.push('/workspace')">进入工作台</el-button>
        <el-button plain @click="router.push('/admin/ai-models')">查看模型配置</el-button>
        <el-button plain @click="router.push('/admin/audit-logs')">查看审计日志</el-button>
      </template>
    </AppPageHeader>

    <div class="grid-4">
      <AppStatCard v-for="metric in metrics" :key="metric.label" :label="metric.label" :value="metric.value" :hint="metric.hint" />
    </div>

    <div class="grid-2">
      <AppSectionCard title="访问趋势" description="最近一段时间的平台访问变化。">
        <div ref="visitChartRef" class="chart-box" />
      </AppSectionCard>
      <AppSectionCard title="下载趋势" description="最近一段时间的资料下载变化。">
        <div ref="downloadChartRef" class="chart-box" />
      </AppSectionCard>
    </div>

    <div class="grid-2">
      <AppSectionCard title="AI 工作原理" description="帮助后台同学快速理解请求、检索、生成和落库链路。">
        <div class="workflow-list">
          <article v-for="step in workflowSteps" :key="step.title" class="workflow-item">
            <div class="workflow-item__index">{{ step.index }}</div>
            <div>
              <div class="workflow-item__title">{{ step.title }}</div>
              <div class="workflow-item__desc">{{ step.description }}</div>
            </div>
          </article>
        </div>
      </AppSectionCard>

      <AppSectionCard title="当前实现重点" description="这一轮平台改造里需要持续守住的交互和系统原则。">
        <div class="principle-list">
          <div v-for="item in principleNotes" :key="item.title" class="principle-item">
            <div class="principle-item__title">{{ item.title }}</div>
            <div class="principle-item__desc">{{ item.description }}</div>
          </div>
        </div>
      </AppSectionCard>
    </div>

    <div class="grid-2">
      <AppSectionCard title="安全日志" description="最近的登录记录与设备信息。" :unpadded="true">
        <AppDataTable :data="dashboard.securityLogs" stripe>
          <el-table-column prop="username" label="账号" min-width="140" />
          <el-table-column prop="loginIp" label="IP" min-width="140" />
          <el-table-column prop="userAgent" label="设备信息" min-width="220" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="时间" min-width="180" />
        </AppDataTable>
      </AppSectionCard>

      <AppSectionCard title="下载日志" description="最近的文件和文档下载操作。" :unpadded="true">
        <AppDataTable :data="dashboard.downloadLogs" stripe>
          <el-table-column prop="operatorUsername" label="操作人" min-width="140" />
          <el-table-column prop="resourceId" label="资源 ID" width="120" />
          <el-table-column prop="detailJson" label="详情" min-width="220" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="时间" min-width="180" />
        </AppDataTable>
      </AppSectionCard>
    </div>

    <AppSectionCard title="近 7 日高频动作" description="帮助确认平台近期最常见的关键动作。" :unpadded="true">
      <AppDataTable :data="dashboard.topActions" stripe>
        <el-table-column prop="actionCode" label="动作编码" min-width="220" />
        <el-table-column prop="total" label="次数" width="120" />
      </AppDataTable>
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getAdminDashboard, type AdminDashboardView } from '@/api/admin'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatCard from '@/components/common/AppStatCard.vue'

const router = useRouter()
const dashboard = ref<AdminDashboardView>()
const visitChartRef = ref<HTMLDivElement>()
const downloadChartRef = ref<HTMLDivElement>()
let visitChart: echarts.ECharts | undefined
let downloadChart: echarts.ECharts | undefined

const workflowSteps = [
  {
    index: '01',
    title: '接收请求与会话上下文',
    description: '前端把项目 ID、会话 ID 和问题发到后端，后端校验权限并记录用户问题。',
  },
  {
    index: '02',
    title: '检索项目知识',
    description: '云侧或中间层基于项目知识块检索相关文档、文件、结构化内容和历史摘要。',
  },
  {
    index: '03',
    title: '模型生成或本地兜底',
    description: '优先调用配置好的模型；如果模型不可用，则回退到基于知识块的本地摘要回答。',
  },
  {
    index: '04',
    title: '引用与消息落库',
    description: '回答、引用片段和会话状态都会写回数据库，方便继续追问和审计。',
  },
  {
    index: '05',
    title: '前端展示与继续追问',
    description: '聊天页展示回答和引用，用户可继续围绕同一项目、同一会话追问。',
  },
]

const principleNotes = [
  {
    title: '知识优先',
    description: '当前 AI 助手不是通用闲聊入口，而是围绕项目知识进行问答。项目没选定时不发请求。',
  },
  {
    title: '引用可追溯',
    description: '回答返回后会附带引用来源，后端也会把引用片段落库，便于后续核查和复盘。',
  },
  {
    title: '流式失败可降级',
    description: '如果 SSE 流式链路不可用，前端会自动退回普通回答接口，而不是直接让界面失效。',
  },
  {
    title: '资料入库链路',
    description: '当前已具备 PDF、Markdown、TXT、DOC、DOCX 文本解析和 Office 内容入库，OCR 仍需额外接入识别引擎。',
  },
]

const metrics = computed(() => {
  if (!dashboard.value) {
    return []
  }
  return [
    {
      label: '用户总数',
      value: dashboard.value.overview.userCount,
      hint: '平台注册用户规模',
    },
    {
      label: '项目总数',
      value: dashboard.value.overview.projectCount,
      hint: '平台知识空间数量',
    },
    {
      label: 'AI 会话总数',
      value: dashboard.value.metrics.aiSessionCount,
      hint: '累计沉淀的会话数量',
    },
    {
      label: '今日活跃用户',
      value: dashboard.value.metrics.activeUsersToday,
      hint: '过去 24 小时内活跃用户',
    },
    {
      label: '24h AI 问答',
      value: dashboard.value.metrics.aiChats24h,
      hint: '过去 24 小时发生的 AI 对话次数',
    },
    {
      label: '24h 失败登录',
      value: dashboard.value.metrics.failedLogins24h,
      hint: '安全监控重点指标',
    },
    {
      label: '24h 下载量',
      value: dashboard.value.metrics.downloads24h,
      hint: '文件和文档下载动作总数',
    },
    {
      label: '搜索次数',
      value: dashboard.value.metrics.searchCount,
      hint: '搜索页和知识检索总量',
    },
  ]
})

const renderCharts = async () => {
  if (!dashboard.value) return
  await nextTick()
  const rootStyle = getComputedStyle(document.documentElement)
  const accent = rootStyle.getPropertyValue('--accent').trim() || '#2563eb'
  const accentSoft = rootStyle.getPropertyValue('--accent-soft').trim() || '#bfdbfe'
  const muted = rootStyle.getPropertyValue('--muted').trim() || '#64748b'
  const line = rootStyle.getPropertyValue('--line').trim() || 'rgba(15, 23, 42, 0.12)'

  if (visitChartRef.value) {
    visitChart ??= echarts.init(visitChartRef.value)
    visitChart.setOption({
      tooltip: { trigger: 'axis' },
      textStyle: { color: muted },
      xAxis: {
        type: 'category',
        data: dashboard.value.visitTrend.map((item) => item.label),
        axisLine: { lineStyle: { color: line } },
        axisLabel: { color: muted },
      },
      yAxis: {
        type: 'value',
        axisLine: { lineStyle: { color: line } },
        splitLine: { lineStyle: { color: line } },
        axisLabel: { color: muted },
      },
      series: [
        {
          data: dashboard.value.visitTrend.map((item) => item.value),
          type: 'line',
          smooth: true,
          areaStyle: { color: accentSoft },
          lineStyle: { color: accent, width: 3 },
          itemStyle: { color: accent },
        },
      ],
      grid: { left: 42, right: 24, top: 24, bottom: 32 },
    })
  }

  if (downloadChartRef.value) {
    downloadChart ??= echarts.init(downloadChartRef.value)
    downloadChart.setOption({
      tooltip: { trigger: 'axis' },
      textStyle: { color: muted },
      xAxis: {
        type: 'category',
        data: dashboard.value.downloadTrend.map((item) => item.label),
        axisLine: { lineStyle: { color: line } },
        axisLabel: { color: muted },
      },
      yAxis: {
        type: 'value',
        axisLine: { lineStyle: { color: line } },
        splitLine: { lineStyle: { color: line } },
        axisLabel: { color: muted },
      },
      series: [
        {
          data: dashboard.value.downloadTrend.map((item) => item.value),
          type: 'bar',
          barWidth: 28,
          itemStyle: { color: accent, borderRadius: [10, 10, 0, 0] },
        },
      ],
      grid: { left: 42, right: 24, top: 24, bottom: 32 },
    })
  }
}

const handleResize = () => {
  visitChart?.resize()
  downloadChart?.resize()
}

onMounted(async () => {
  dashboard.value = await getAdminDashboard()
  await renderCharts()
  window.addEventListener('resize', handleResize)
})

watch(dashboard, () => {
  void renderCharts()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  visitChart?.dispose()
  downloadChart?.dispose()
})
</script>

<style scoped>
.admin-dashboard {
  gap: 22px;
}

.workflow-list,
.principle-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.workflow-item,
.principle-item {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 14px;
  align-items: flex-start;
  padding: 14px;
  border-radius: 20px;
  border: 1px solid var(--line);
  background: var(--surface-strong);
}

.workflow-item__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: var(--panel);
  color: var(--accent);
  font-weight: 800;
}

.workflow-item__title,
.principle-item__title {
  font-weight: 700;
}

.workflow-item__desc,
.principle-item__desc {
  margin-top: 6px;
  color: var(--muted);
  line-height: 1.7;
}

.principle-item {
  grid-template-columns: 1fr;
}
</style>
