<template>
  <div class="page-shell admin-dashboard">
    <el-skeleton v-if="loading" animated class="admin-dashboard__state">
      <template #template>
        <el-skeleton-item variant="h1" style="width: 280px; height: 38px;" />
        <el-skeleton-item variant="text" style="width: 460px; margin-top: 16px;" />
        <el-skeleton-item variant="rect" style="width: 100%; height: 180px; margin-top: 24px; border-radius: 24px;" />
        <el-skeleton-item variant="rect" style="width: 100%; height: 320px; margin-top: 18px; border-radius: 24px;" />
      </template>
    </el-skeleton>

    <el-result v-else-if="errorText" class="admin-dashboard__state" icon="warning" title="仪表盘加载失败"
      :sub-title="errorText">
      <template #extra>
        <el-button type="primary" @click="loadDashboard">重新加载</el-button>
      </template>
    </el-result>

    <template v-else-if="normalizedDashboard">

      <section class="admin-dashboard__hero">
        <article v-for="card in headlineCards" :key="card.label" class="hero-stat">
          <div class="hero-stat__icon">
            <span class="material-symbols-outlined">{{ card.icon }}</span>
          </div>
          <div class="hero-stat__copy">
            <span>{{ card.label }}</span>
            <strong>{{ card.value }}</strong>
            <small>{{ card.hint }}</small>
          </div>
        </article>
      </section>

      <section class="admin-dashboard__metrics">
        <AppSectionCard title="用户数据">
          <div class="metric-grid">
            <article v-for="metric in resourceMetrics" :key="metric.label" class="metric-card">
              <span>{{ metric.label }}</span>
              <strong>{{ metric.value }}</strong>
              <small>{{ metric.hint }}</small>
            </article>
          </div>
        </AppSectionCard>

        <AppSectionCard title="令牌消耗概览">
          <div class="token-summary">
            <div class="token-ring" :style="tokenRingStyle">
              <div class="token-ring__value">{{ compactNumber(normalizedDashboard.tokenUsage.totalTokens24h) }}</div>
              <div class="token-ring__label">24小时令牌</div>
            </div>
            <div class="token-breakdown">
              <div class="token-breakdown__row">
                <span>近 7 日输入令牌</span>
                <strong>{{ compactNumber(normalizedDashboard.tokenUsage.promptTokens7d) }}</strong>
              </div>
              <div class="token-breakdown__bar">
                <div class="is-prompt" :style="{ width: `${tokenPromptRatio}%` }"></div>
              </div>
              <div class="token-breakdown__row">
                <span>近 7 日输出令牌</span>
                <strong>{{ compactNumber(normalizedDashboard.tokenUsage.completionTokens7d) }}</strong>
              </div>
              <div class="token-breakdown__bar">
                <div class="is-completion" :style="{ width: `${tokenCompletionRatio}%` }"></div>
              </div>
              <div class="token-breakdown__row token-breakdown__row--total">
                <span>近 7 日总令牌</span>
                <strong>{{ compactNumber(normalizedDashboard.tokenUsage.totalTokens7d) }}</strong>
              </div>
            </div>
          </div>
        </AppSectionCard>
      </section>

      <section class="admin-dashboard__charts">
        <AppSectionCard title="每日访问流量">
          <div ref="visitChartRef" class="chart-box"></div>
        </AppSectionCard>

        <AppSectionCard title="每日接口调用">
          <div ref="apiChartRef" class="chart-box"></div>
        </AppSectionCard>

        <AppSectionCard title="每日令牌消耗">
          <div ref="tokenChartRef" class="chart-box"></div>
        </AppSectionCard>
      </section>

      <section class="admin-dashboard__tables">
      <AppSectionCard title="最近失败登录" :unpadded="true">
        <AppDataTable :data="normalizedDashboard.securityLogs" stripe :max-height="520">
          <el-table-column prop="username" label="账号" min-width="140" />
          <el-table-column prop="loginIp" label="IP 地址" min-width="140" />
          <el-table-column prop="userAgent" label="设备信息" min-width="220" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="时间" min-width="180" />
        </AppDataTable>
      </AppSectionCard>

      <AppSectionCard title="高频动作" description="近 7 日最常触发的关键动作。" :unpadded="true">
        <AppDataTable :data="normalizedDashboard.topActions" stripe :max-height="520">
          <el-table-column label="动作类型" min-width="220">
            <template #default="{ row }">{{ actionLabel(row.actionCode) }}</template>
          </el-table-column>
          <el-table-column prop="total" label="次数" width="120" />
        </AppDataTable>
      </AppSectionCard>
      </section>
    </template>
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
import { resolveErrorMessage } from '@/utils/formatters'

const router = useRouter()
const dashboard = ref<(Partial<AdminDashboardView> & { downloadTrend?: Array<{ label: string; value: number }> })>()
const loading = ref(true)
const errorText = ref('')
const visitChartRef = ref<HTMLDivElement>()
const apiChartRef = ref<HTMLDivElement>()
const tokenChartRef = ref<HTMLDivElement>()
let visitChart: echarts.ECharts | undefined
let apiChart: echarts.ECharts | undefined
let tokenChart: echarts.ECharts | undefined
const chartPalette = ['#0060a9', '#22c55e', '#f59e0b', '#8b5cf6', '#ef4444', '#06b6d4', '#84cc16', '#f97316']

const resolveChart = (element: HTMLDivElement | undefined, chart?: echarts.ECharts) => {
  if (!element) {
    return chart
  }
  if (!chart) {
    return echarts.init(element)
  }
  if (chart.getDom() !== element) {
    chart.dispose()
    return echarts.init(element)
  }
  return chart
}

const normalizedDashboard = computed(() => {
  if (!dashboard.value) return null
  return {
    overview: {
      userCount: dashboard.value.overview?.userCount || 0,
      projectCount: dashboard.value.overview?.projectCount || 0,
      fileCount: dashboard.value.overview?.fileCount || 0,
      documentCount: dashboard.value.overview?.documentCount || 0,
    },
    metrics: {
      aiSessionCount: dashboard.value.metrics?.aiSessionCount || 0,
      searchCount: dashboard.value.metrics?.searchCount || 0,
      activeUsersToday: dashboard.value.metrics?.activeUsersToday || 0,
      failedLogins24h: dashboard.value.metrics?.failedLogins24h || 0,
      downloads24h: dashboard.value.metrics?.downloads24h || 0,
      aiChats24h: dashboard.value.metrics?.aiChats24h || 0,
    },
    visitSeries: dashboard.value.visitSeries || [],
    apiCallSeries: dashboard.value.apiCallSeries || [],
    tokenSeries: dashboard.value.tokenSeries || [],
    tokenUsage: {
      promptTokens7d: dashboard.value.tokenUsage?.promptTokens7d || 0,
      completionTokens7d: dashboard.value.tokenUsage?.completionTokens7d || 0,
      totalTokens7d: dashboard.value.tokenUsage?.totalTokens7d || 0,
      totalTokens24h: dashboard.value.tokenUsage?.totalTokens24h || 0,
      apiCalls24h: dashboard.value.tokenUsage?.apiCalls24h || 0,
    },
    securityLogs: dashboard.value.securityLogs || [],
    downloadLogs: dashboard.value.downloadLogs || [],
    topActions: dashboard.value.topActions || [],
  }
})

const compactNumber = (value: number) => {
  if (value >= 1000000) return `${(value / 1000000).toFixed(1)}M`
  if (value >= 1000) return `${(value / 1000).toFixed(1)}k`
  return String(value)
}

const headlineCards = computed(() => {
  if (!normalizedDashboard.value) return []
  return [
    {
      label: '今日页面浏览量',
      value: normalizedDashboard.value.visitSeries.find((item) => item.key === 'pv')?.points.at(-1)?.value || 0,
      hint: '按登录日志统计当日浏览量',
      icon: 'monitoring',
    },
    {
      label: '24小时接口调用',
      value: normalizedDashboard.value.tokenUsage.apiCalls24h,
      hint: '智能请求日志总量',
      icon: 'api',
    },
    {
      label: '24小时令牌消耗',
      value: compactNumber(normalizedDashboard.value.tokenUsage.totalTokens24h),
      hint: '输入与输出令牌总和',
      icon: 'neurology',
    },
    {
      label: '今日活跃用户',
      value: normalizedDashboard.value.metrics.activeUsersToday,
      hint: '当日有登录行为的用户',
      icon: 'group',
    },
  ]
})

const resourceMetrics = computed(() => {
  if (!normalizedDashboard.value) return []
  return [
    { label: '用户总数', value: normalizedDashboard.value.overview.userCount, hint: '平台注册账号' },
    { label: '项目总数', value: normalizedDashboard.value.overview.projectCount, hint: '知识空间总量' },
    { label: '文件总数', value: normalizedDashboard.value.overview.fileCount, hint: '已上传文件' },
    { label: '文档总数', value: normalizedDashboard.value.overview.documentCount, hint: '在线文档' },
    { label: '智能会话总数', value: normalizedDashboard.value.metrics.aiSessionCount, hint: '累计会话' },
    { label: '搜索次数', value: normalizedDashboard.value.metrics.searchCount, hint: '站内检索动作' },
  ]
})

const actionLabel = (value?: string) => ({
  FILE_UPLOAD: '文件上传',
  FILE_DOWNLOAD: '文件下载',
  FILE_PREVIEW: '文件预览',
  FILE_DELETE: '文件删除',
  DOCUMENT_SAVE: '文档保存',
  DOCUMENT_PUBLISH: '文档发布',
  DOCUMENT_DELETE: '文档删除',
  SEARCH: '站内搜索',
  AI_CHAT: '智能问答',
  AI_SESSION_CREATE: '创建智能会话',
  AI_SESSION_DELETE: '删除智能会话',
  ADMIN_USER_STATUS: '修改用户状态',
  ADMIN_TASK_RETRY: '重试后台任务',
  ADMIN_MODEL_UPDATE: '更新模型配置',
}[String(value || '').toUpperCase()] || value || '未命名动作')

const tokenPromptRatio = computed(() => {
  if (!normalizedDashboard.value?.tokenUsage.totalTokens7d) return 0
  return Math.round((normalizedDashboard.value.tokenUsage.promptTokens7d / normalizedDashboard.value.tokenUsage.totalTokens7d) * 100)
})

const tokenCompletionRatio = computed(() => {
  if (!normalizedDashboard.value?.tokenUsage.totalTokens7d) return 0
  return Math.round((normalizedDashboard.value.tokenUsage.completionTokens7d / normalizedDashboard.value.tokenUsage.totalTokens7d) * 100)
})

const tokenRingStyle = computed(() => {
  const promptRatio = tokenPromptRatio.value
  return {
    background: `radial-gradient(circle at center, #ffffff 56%, transparent 57%), conic-gradient(#0060a9 0 ${promptRatio}%, #22c55e ${promptRatio}% 100%)`,
  }
})

const buildEmptyChartOption = (title: string) => ({
  animation: false,
  grid: { left: 0, right: 0, top: 0, bottom: 0 },
  xAxis: { show: false, type: 'category', data: [] },
  yAxis: { show: false, type: 'value' },
  series: [],
  graphic: {
    type: 'group',
    left: 'center',
    top: 'middle',
    children: [
      {
        type: 'text',
        style: {
          text: title,
          fill: '#64748b',
          fontSize: 15,
          fontWeight: 600,
        },
      },
    ],
  },
})

const buildSeries = (
  series: Array<{ key: string; label: string; points: Array<{ label: string; value: number }> }>,
  type: 'line' | 'bar',
  extra?: (color: string) => Record<string, unknown>,
) =>
  series.map((item, index) => {
    const color = chartPalette[index % chartPalette.length]
    return {
      name: item.label,
      type,
      data: item.points.map((point) => point.value),
      ...(type === 'line'
        ? {
          smooth: true,
          lineStyle: { color, width: 3 },
          itemStyle: { color },
        }
        : {
          barWidth: 24,
          itemStyle: { color, borderRadius: [10, 10, 0, 0] },
        }),
      ...(extra ? extra(color) : {}),
    }
  })

const chartCategoryLabels = computed(() => {
  if (!normalizedDashboard.value) return []
  const baseSeries =
    normalizedDashboard.value.visitSeries[0] ||
    normalizedDashboard.value.apiCallSeries[0] ||
    normalizedDashboard.value.tokenSeries[0]
  return baseSeries?.points.map((item) => item.label.slice(5)) || []
})

const renderCharts = async () => {
  if (!normalizedDashboard.value) return
  await nextTick()

  const rootStyle = getComputedStyle(document.documentElement)
  const accent = rootStyle.getPropertyValue('--accent').trim() || '#2563eb'
  const accentSoft = rootStyle.getPropertyValue('--accent-soft').trim() || '#bfdbfe'
  const muted = rootStyle.getPropertyValue('--muted').trim() || '#64748b'
  const line = rootStyle.getPropertyValue('--line').trim() || 'rgba(15, 23, 42, 0.12)'

  if (visitChartRef.value) {
    const currentVisitChart = resolveChart(visitChartRef.value, visitChart)
    if (!currentVisitChart) return
    visitChart = currentVisitChart
    const visitSeries = buildSeries(normalizedDashboard.value.visitSeries, 'line')
    const hasVisitData = visitSeries.some((item) => item.data.some((value: number) => value > 0))
    if (!hasVisitData) {
      currentVisitChart.setOption(buildEmptyChartOption('暂无访问流量数据'), true)
      return
    }
    currentVisitChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: {
        top: 8,
        textStyle: { color: muted },
      },
      color: chartPalette,
      grid: { left: 42, right: 20, top: 52, bottom: 28 },
      xAxis: {
        type: 'category',
        data: chartCategoryLabels.value,
        axisLabel: { color: muted },
        axisLine: { lineStyle: { color: line } },
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: muted },
        splitLine: { lineStyle: { color: line } },
      },
      series: visitSeries.map((item, index) => ({
        ...item,
        areaStyle: index === 0 ? { color: accentSoft } : undefined,
      })),
    })
  }

  if (apiChartRef.value) {
    const currentApiChart = resolveChart(apiChartRef.value, apiChart)
    if (!currentApiChart) return
    apiChart = currentApiChart
    const apiSeries = buildSeries(normalizedDashboard.value.apiCallSeries, 'line')
    const hasApiData = apiSeries.some((item) => item.data.some((value: number) => value > 0))
    if (!hasApiData) {
      currentApiChart.setOption(buildEmptyChartOption('暂无接口调用数据'), true)
      return
    }
    currentApiChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: {
        top: 8,
        textStyle: { color: muted },
      },
      color: chartPalette,
      grid: { left: 42, right: 20, top: 52, bottom: 28 },
      xAxis: {
        type: 'category',
        data: chartCategoryLabels.value,
        axisLabel: { color: muted },
        axisLine: { lineStyle: { color: line } },
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: muted },
        splitLine: { lineStyle: { color: line } },
      },
      series: apiSeries,
    })
  }

  if (tokenChartRef.value) {
    const currentTokenChart = resolveChart(tokenChartRef.value, tokenChart)
    if (!currentTokenChart) return
    tokenChart = currentTokenChart
    const tokenSeries = buildSeries(normalizedDashboard.value.tokenSeries, 'bar', () => ({ stack: 'tokens' }))
    const hasTokenData = tokenSeries.some((item) => item.data.some((value: number) => value > 0))
    if (!hasTokenData) {
      currentTokenChart.setOption(buildEmptyChartOption('暂无令牌消耗数据'), true)
      return
    }
    currentTokenChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: {
        top: 8,
        textStyle: { color: muted },
      },
      color: chartPalette,
      grid: { left: 42, right: 20, top: 52, bottom: 28 },
      xAxis: {
        type: 'category',
        data: chartCategoryLabels.value,
        axisLabel: { color: muted },
        axisLine: { lineStyle: { color: line } },
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: muted },
        splitLine: { lineStyle: { color: line } },
      },
      series: tokenSeries,
    })
  }
}

const handleResize = () => {
  visitChart?.resize()
  apiChart?.resize()
  tokenChart?.resize()
}

const loadDashboard = async () => {
  loading.value = true
  errorText.value = ''
  try {
    dashboard.value = await getAdminDashboard()
  } catch (error) {
    errorText.value = resolveErrorMessage(error, '管理员仪表盘数据暂时不可用，请稍后重试。')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadDashboard()
  window.addEventListener('resize', handleResize)
})

watch(
  [loading, normalizedDashboard],
  async ([isLoading, currentDashboard]) => {
    if (isLoading || !currentDashboard) {
      return
    }
    await nextTick()
    await renderCharts()
  },
  { flush: 'post' },
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  visitChart?.dispose()
  apiChart?.dispose()
  tokenChart?.dispose()
})
</script>

<style scoped>
.admin-dashboard {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.admin-dashboard__state {
  min-height: 420px;
}

.admin-dashboard__hero {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.hero-stat {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 14px;
  padding: 18px;
  border-radius: 22px;
  border: 1px solid rgba(192, 199, 212, 0.22);
  background:
    radial-gradient(circle at top right, rgba(0, 96, 169, 0.1), transparent 38%),
    #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}

.hero-stat__icon {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  background: rgba(0, 96, 169, 0.1);
  color: #0060a9;
}

.hero-stat__copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.hero-stat__copy span {
  color: #667085;
  font-size: 13px;
  font-weight: 700;
}

.hero-stat__copy strong {
  font-size: 28px;
  line-height: 1.1;
  color: #111827;
}

.hero-stat__copy small {
  color: #7a8392;
  line-height: 1.5;
}

.admin-dashboard__metrics {
  display: grid;
  grid-template-columns: 1.25fr 1fr;
  gap: 18px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid var(--line);
  background: var(--surface-strong);
}

.metric-card span,
.token-breakdown__row span {
  color: #667085;
  font-size: 13px;
}

.metric-card strong,
.token-breakdown__row strong {
  font-size: 22px;
  color: #101828;
}

.metric-card small {
  color: #7a8392;
  line-height: 1.5;
}

.token-summary {
  display: grid;
  grid-template-columns: 180px 1fr;
  gap: 18px;
  align-items: center;
}

.token-ring {
  width: 160px;
  height: 160px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background:
    radial-gradient(circle at center, #ffffff 56%, transparent 57%),
    conic-gradient(#0060a9 0 50%, #22c55e 50% 100%);
  border: 1px solid rgba(0, 96, 169, 0.12);
}

.token-ring__value {
  font-size: 28px;
  font-weight: 800;
  color: #101828;
}

.token-ring__label {
  margin-top: 6px;
  color: #667085;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.token-breakdown {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.token-breakdown__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.token-breakdown__row--total {
  margin-top: 6px;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
}

.token-breakdown__bar {
  width: 100%;
  height: 10px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(192, 199, 212, 0.22);
}

.token-breakdown__bar>div {
  height: 100%;
  border-radius: inherit;
}

.token-breakdown__bar .is-prompt {
  background: #0060a9;
}

.token-breakdown__bar .is-completion {
  background: #22c55e;
}

.admin-dashboard__charts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.chart-box {
  height: 280px;
}

.admin-dashboard__tables {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 18px;
}

@media (max-width: 1200px) {

  .admin-dashboard__hero,
  .admin-dashboard__charts,
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .admin-dashboard__metrics,
  .admin-dashboard__tables {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {

  .admin-dashboard__hero,
  .admin-dashboard__charts,
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .token-summary {
    grid-template-columns: 1fr;
    justify-items: center;
  }
}
</style>
