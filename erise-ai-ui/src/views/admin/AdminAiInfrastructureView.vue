<template>
  <div class="section-stack ai-infrastructure-page">
    <section v-if="overviewLoading" class="summary-grid">
      <article v-for="index in 4" :key="index" class="summary-card is-loading">
        <el-skeleton animated :rows="2" />
      </article>
    </section>

    <section v-else-if="overview" class="summary-grid">
      <article class="summary-card">
        <span class="material-symbols-outlined">monitor_heart</span>
        <div>
          <strong>{{ statusLabel(overview.serviceHealth.status) }}</strong>
          <span>AI 服务总览</span>
        </div>
      </article>
      <article class="summary-card">
        <span class="material-symbols-outlined">deployed_code</span>
        <div>
          <strong>{{ defaultProviderLabel }}</strong>
          <span>当前默认 Provider</span>
        </div>
      </article>
      <article class="summary-card">
        <span class="material-symbols-outlined">sync_alt</span>
        <div>
          <strong>{{ formatPercent(overview.n8nSummary.successRate) }}</strong>
          <span>n8n 最近 {{ overview.n8nSummary.windowHours }} 小时送达成功率</span>
        </div>
      </article>
      <article class="summary-card">
        <span class="material-symbols-outlined">description</span>
        <div>
          <strong>{{ overview.fileCapabilities.fileTypes.length }}</strong>
          <span>已声明文档类型能力</span>
        </div>
      </article>
    </section>

    <el-result v-else-if="overviewError" class="page-state" icon="warning" title="AI 基础设施概览加载失败"
      :sub-title="overviewError">
      <template #extra>
        <el-button type="primary" @click="loadOverview">重新加载</el-button>
      </template>
    </el-result>

    <section v-if="overviewWarnings.length" class="warning-stack">
      <el-alert v-for="warning in overviewWarnings" :key="`${warning.section}-${warning.message}`" type="warning"
        show-icon :closable="false" :title="infrastructureWarningTitle(warning.section)"
        :description="warning.message" />
    </section>

    <AppSectionCard title="Provider 健康与路由" :unpadded="true">
      <template #actions>
        <div class="section-actions">
          <span v-if="overview?.providers.generatedAt" class="section-meta">更新于 {{
            formatDateTime(overview.providers.generatedAt) }}</span>
          <el-button @click="loadOverview">刷新</el-button>
        </div>
      </template>

      <div v-if="overviewLoading" class="page-state">
        <el-skeleton animated :rows="8" />
      </div>

      <el-result v-else-if="overviewError" class="page-state" icon="warning" title="Provider 信息加载失败"
        :sub-title="overviewError">
        <template #extra>
          <el-button type="primary" @click="loadOverview">重新加载</el-button>
        </template>
      </el-result>

      <template v-else-if="overview">
        <div class="status-strip">
          <div class="status-chip">
            <span>数据库</span>
            <AppStatusTag :label="statusLabel(overview.serviceHealth.database)"
              :tone="statusTone(overview.serviceHealth.database)" />
          </div>
          <div class="status-chip">
            <span>Redis</span>
            <AppStatusTag :label="statusLabel(overview.serviceHealth.redis)"
              :tone="statusTone(overview.serviceHealth.redis)" />
          </div>
          <div class="status-chip">
            <span>Provider 摘要</span>
            <AppStatusTag :label="statusLabel(overview.serviceHealth.providers.status)"
              :tone="statusTone(overview.serviceHealth.providers.status)" />
          </div>
        </div>

        <div class="provider-grid">
          <article v-for="route in overview.providers.effectiveRoutes"
            :key="`${route.role}-${route.providerCode}-${route.modelCode}`" class="provider-card">
            <div class="provider-card__header">
              <div>
                <div class="provider-card__eyebrow">{{ roleLabel(route.role) }}</div>
                <h3>{{ route.modelName || route.modelCode }}</h3>
              </div>
              <AppStatusTag :label="statusLabel(route.status)" :tone="statusTone(route.status)" />
            </div>

            <dl class="provider-card__details">
              <div>
                <dt>Provider</dt>
                <dd>{{ route.providerCode }}</dd>
              </div>
              <div>
                <dt>Base URL</dt>
                <dd>{{ route.baseUrl || '--' }}</dd>
              </div>
              <div>
                <dt>Endpoint</dt>
                <dd>{{ route.endpointUrl || '--' }}</dd>
              </div>
              <div>
                <dt>超时</dt>
                <dd>{{ route.timeoutSeconds }} 秒</dd>
              </div>
              <div>
                <dt>24h 请求</dt>
                <dd>{{ route.recentRequestCount24h }}</dd>
              </div>
              <div>
                <dt>24h 错误</dt>
                <dd>{{ route.recentErrorCount24h }}</dd>
              </div>
            </dl>

            <div class="provider-card__footer">
              <span>来源：{{ route.source || '--' }}</span>
              <span>探测耗时：{{ route.latencyMs ? `${route.latencyMs} ms` : '--' }}</span>
            </div>

            <div v-if="route.errorCode || route.message" class="alert-box danger">
              <strong>{{ route.errorCode || 'PROVIDER_ERROR' }}</strong>
              <span>{{ route.message || 'Provider 探测失败' }}</span>
            </div>

            <div v-if="route.recentErrorCodes.length" class="tag-row">
              <el-tag v-for="item in route.recentErrorCodes" :key="`${route.modelCode}-${item.errorCode}`"
                effect="plain" type="danger">
                {{ item.errorCode }} × {{ item.count }}
              </el-tag>
            </div>
          </article>
        </div>

        <div class="table-shell">
          <AppDataTable :data="overview.providers.enabledRoutes" stripe>
            <el-table-column label="模型" min-width="220">
              <template #default="{ row }">
                <div class="two-line-cell">
                  <strong>{{ row.modelName || row.modelCode }}</strong>
                  <span>{{ row.providerCode }} / {{ row.modelCode }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="角色" width="120">
              <template #default="{ row }">{{ roleLabel(row.role) }}</template>
            </el-table-column>
            <el-table-column label="默认 / 生效" width="140">
              <template #default="{ row }">
                <div class="inline-tags">
                  <AppStatusTag :label="row.isDefault ? '默认' : '候选'" :tone="row.isDefault ? 'primary' : 'info'" />
                  <AppStatusTag :label="row.isEffective ? '生效' : '待命'"
                    :tone="row.isEffective ? 'success' : 'warning'" />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <AppStatusTag :label="statusLabel(row.status)" :tone="statusTone(row.status)" />
              </template>
            </el-table-column>
            <el-table-column label="Base URL" min-width="280" show-overflow-tooltip>
              <template #default="{ row }">{{ row.baseUrl || '--' }}</template>
            </el-table-column>
            <el-table-column label="24h 请求" width="100">
              <template #default="{ row }">{{ row.recentRequestCount24h }}</template>
            </el-table-column>
            <el-table-column label="24h 错误" width="100">
              <template #default="{ row }">{{ row.recentErrorCount24h }}</template>
            </el-table-column>
            <el-table-column label="最近错误" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ formatRecentErrors(row.recentErrorCodes) }}</template>
            </el-table-column>
          </AppDataTable>
        </div>
      </template>
    </AppSectionCard>

    <AppSectionCard title="n8n 事件治理" :unpadded="true">
      <template #actions>
        <el-button @click="refreshN8n">刷新</el-button>
      </template>

      <div v-if="eventsLoading && !records.length" class="page-state">
        <el-skeleton animated :rows="10" />
      </div>

      <el-result v-else-if="eventsError && !records.length" class="page-state" icon="warning" title="n8n 事件加载失败"
        :sub-title="eventsError">
        <template #extra>
          <el-button type="primary" @click="refreshN8n">重新加载</el-button>
        </template>
      </el-result>

      <template v-else>
        <div v-if="overview" class="n8n-metrics-grid">
          <article class="metric-card">
            <strong>{{ overview.n8nSummary.totalEvents }}</strong>
            <span>最近 {{ overview.n8nSummary.windowHours }} 小时事件总数</span>
          </article>
          <article class="metric-card success">
            <strong>{{ overview.n8nSummary.deliveredCount }}</strong>
            <span>送达成功</span>
          </article>
          <article class="metric-card danger">
            <strong>{{ overview.n8nSummary.failedCount }}</strong>
            <span>送达失败</span>
          </article>
          <article class="metric-card warning">
            <strong>{{ overview.n8nSummary.workflowFailedCount }}</strong>
            <span>工作流执行失败</span>
          </article>
          <article class="metric-card info">
            <strong>{{ overview.n8nSummary.manualPendingCount }}</strong>
            <span>人工待处理</span>
          </article>
        </div>

        <div v-if="overview" class="insight-grid">
          <section class="insight-panel">
            <header>
              <h3>失败归因</h3>
              <span>可人工重发 {{ overview.n8nSummary.retryableFailedCount }} 条</span>
            </header>
            <div class="insight-stat-list">
              <div>
                <label>送达失败</label>
                <strong>{{ overview.n8nSummary.failedCount }}</strong>
              </div>
              <div>
                <label>工作流失败</label>
                <strong>{{ overview.n8nSummary.workflowFailedCount }}</strong>
              </div>
              <div>
                <label>工作流运行中</label>
                <strong>{{ overview.n8nSummary.workflowRunningCount }}</strong>
              </div>
              <div>
                <label>待人工处理</label>
                <strong>{{ overview.n8nSummary.manualPendingCount }}</strong>
              </div>
            </div>
            <div v-if="overview.n8nSummary.topErrorCodes.length" class="tag-row">
              <el-tag v-for="item in overview.n8nSummary.topErrorCodes" :key="item.errorCode" effect="plain"
                type="danger">
                {{ item.errorCode }} × {{ item.count }}
              </el-tag>
            </div>
            <el-empty v-else :image-size="64" description="最近窗口内没有失败错误码" />
          </section>

          <section class="insight-panel">
            <header>
              <h3>最新失败事件</h3>
              <span>{{ overview.n8nSummary.latestFailure ? '可直接进入详情' : '暂无失败事件' }}</span>
            </header>
            <div v-if="overview.n8nSummary.latestFailure" class="latest-failure-card">
              <div class="two-line-cell">
                <strong>{{ overview.n8nSummary.latestFailure.eventType }}</strong>
                <span>{{ overview.n8nSummary.latestFailure.requestId }}</span>
              </div>
              <div class="inline-tags">
                <AppStatusTag :label="deliveryLabel(overview.n8nSummary.latestFailure.deliveryStatus)"
                  :tone="statusTone(overview.n8nSummary.latestFailure.deliveryStatus)" />
                <AppStatusTag :label="workflowStatusLabel(overview.n8nSummary.latestFailure.workflowStatus)"
                  :tone="statusTone(overview.n8nSummary.latestFailure.workflowStatus)" />
              </div>
              <p>{{ overview.n8nSummary.latestFailure.workflowErrorSummary ||
                overview.n8nSummary.latestFailure.errorMessage || '无错误摘要' }}</p>
              <el-button type="primary" link @click="openDetail(overview.n8nSummary.latestFailure)">查看详情</el-button>
            </div>
            <el-empty v-else :image-size="64" description="最近窗口内没有失败事件" />
          </section>
        </div>

        <AppFilterBar>
          <el-input v-model="filters.q" clearable placeholder="搜索 requestId、workflow、执行 ID、错误码" @clear="handleSearch"
            @keyup.enter="handleSearch" />
          <el-select v-model="filters.deliveryStatus" clearable placeholder="送达状态" @change="handleSearch">
            <el-option label="送达成功" value="DELIVERED" />
            <el-option label="送达失败" value="FAILED" />
            <el-option label="待处理" value="PENDING" />
            <el-option label="已跳过" value="SKIPPED" />
          </el-select>
          <el-select v-model="filters.workflowStatus" clearable placeholder="工作流状态" @change="handleSearch">
            <el-option label="未启动" value="NOT_STARTED" />
            <el-option label="待处理" value="PENDING" />
            <el-option label="运行中" value="RUNNING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已失败" value="FAILED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
          <el-select v-model="filters.manualStatus" clearable placeholder="人工治理状态" @change="handleSearch">
            <el-option label="待人工处理" value="PENDING" />
            <el-option label="已人工闭环" value="RESOLVED" />
          </el-select>
          <el-input v-model="filters.eventType" clearable placeholder="事件类型" @clear="handleSearch"
            @keyup.enter="handleSearch" />
          <el-date-picker v-model="filters.createdDate" type="date" value-format="YYYY-MM-DD" placeholder="创建日期"
            @change="handleSearch" />
          <template #actions>
            <el-button @click="resetFilters">重置</el-button>
          </template>
        </AppFilterBar>

        <div v-if="eventsError" class="inline-error-tip">{{ eventsError }}</div>

        <AppDataTable :data="records" stripe>
          <el-table-column label="事件" min-width="220">
            <template #default="{ row }">
              <div class="two-line-cell">
                <strong>{{ row.eventType }}</strong>
                <span>{{ row.requestId }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="工作流" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="two-line-cell">
                <strong>{{ row.workflowName || row.workflowHint || '--' }}</strong>
                <span>{{ workflowMetaLabel(row) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="送达层" width="120">
            <template #default="{ row }">
              <AppStatusTag :label="deliveryLabel(row.deliveryStatus)" :tone="statusTone(row.deliveryStatus)" />
            </template>
          </el-table-column>
          <el-table-column label="工作流层" width="120">
            <template #default="{ row }">
              <AppStatusTag :label="workflowStatusLabel(row.workflowStatus)" :tone="statusTone(row.workflowStatus)" />
            </template>
          </el-table-column>
          <el-table-column label="人工治理" width="140">
            <template #default="{ row }">
              <div class="two-line-cell">
                <AppStatusTag :label="manualStatusLabel(row.manualStatus)" :tone="manualStatusTone(row.manualStatus)" />
                <span>重发 {{ row.manualReplayCount || 0 }} 次</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="尝试次数" width="110">
            <template #default="{ row }">{{ row.attemptCount }} / {{ row.maxAttempts }}</template>
          </el-table-column>
          <el-table-column label="执行信息" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="two-line-cell">
                <strong>{{ row.externalExecutionId || row.errorCode || '--' }}</strong>
                <span>{{ row.workflowErrorSummary || row.errorMessage || '--' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button type="primary" link @click="openDetail(row)">详情</el-button>
                <el-button type="primary" link :disabled="!row.workflowHint || retryingId === row.id"
                  @click="handleRetry(row)">
                  {{ retryingId === row.id ? '处理中...' : '人工重发' }}
                </el-button>
                <el-button type="warning" link :disabled="!allowManualHandoff(row) || handingOffId === row.id"
                  @click="handleManualHandoff(row)">
                  {{ handingOffId === row.id ? '处理中...' : '转人工' }}
                </el-button>
              </div>
            </template>
          </el-table-column>
        </AppDataTable>

        <div class="table-footer">
          <span class="table-count">共 {{ total }} 条事件</span>
          <CompactPager variant="project" :page-num="pageNum" :page-size="pageSize" :total="total"
            @change="handlePageChange" />
        </div>
      </template>
    </AppSectionCard>

    <AppSectionCard title="文档解析能力" :unpadded="true">
      <template #actions>
        <el-button @click="loadOverview">刷新</el-button>
      </template>

      <div v-if="overviewLoading" class="page-state">
        <el-skeleton animated :rows="8" />
      </div>

      <el-result v-else-if="overviewError" class="page-state" icon="warning" title="解析能力加载失败"
        :sub-title="overviewError">
        <template #extra>
          <el-button type="primary" @click="loadOverview">重新加载</el-button>
        </template>
      </el-result>

      <template v-else-if="overview">
        <div class="status-strip">
          <div class="status-chip">
            <span>解析顺序</span>
            <strong>{{ overview.fileCapabilities.parserOrder.join(' → ') || '--' }}</strong>
          </div>
          <div class="status-chip">
            <span>Parse Status</span>
            <strong>{{ overview.fileCapabilities.parseStatuses.join(' / ') || '--' }}</strong>
          </div>
          <div class="status-chip">
            <span>支持类型</span>
            <strong>{{ overview.fileCapabilities.fileTypes.length }}</strong>
          </div>
        </div>

        <div class="runtime-grid">
          <article v-for="runtime in overview.fileCapabilities.parserRuntimes" :key="runtime.parserCode"
            class="runtime-card">
            <div class="runtime-card__header">
              <div>
                <h3>{{ runtime.label }}</h3>
                <p>{{ runtime.parserCode }}</p>
              </div>
              <AppStatusTag :label="statusLabel(runtime.status)" :tone="statusTone(runtime.status)" />
            </div>
            <p class="runtime-card__copy">
              {{ runtime.message || '运行时依赖已就绪，可直接被后台能力矩阵消费。' }}
            </p>
            <div class="runtime-card__meta">
              <span>支持类型</span>
              <strong>{{runtime.supportedExtensions.map((item) => item.toUpperCase()).join('、') || '--'}}</strong>
            </div>
            <div v-if="runtime.errorCode" class="alert-box danger">{{ runtime.errorCode }}</div>
          </article>
        </div>

        <AppDataTable :data="overview.fileCapabilities.fileTypes" stripe>
          <el-table-column label="类型" min-width="180">
            <template #default="{ row }">
              <div class="two-line-cell">
                <strong>{{ row.label }}</strong>
                <span>.{{ row.extension }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="主解析器" width="140">
            <template #default="{ row }">{{ row.primaryParser }}</template>
          </el-table-column>
          <el-table-column label="Fallback" width="140">
            <template #default="{ row }">{{ row.fallbackParser || '--' }}</template>
          </el-table-column>
          <el-table-column label="OCR" width="90">
            <template #default="{ row }">
              <AppStatusTag :label="row.supportsOcr ? '支持' : '无'" :tone="row.supportsOcr ? 'success' : 'info'" />
            </template>
          </el-table-column>
          <el-table-column label="分页能力" width="110">
            <template #default="{ row }">
              <AppStatusTag :label="row.supportsPages ? '支持' : '不支持'" :tone="row.supportsPages ? 'primary' : 'info'" />
            </template>
          </el-table-column>
          <el-table-column label="状态集合" min-width="200">
            <template #default="{ row }">{{ row.parseStatuses.join(' / ') || '--' }}</template>
          </el-table-column>
          <el-table-column label="重试策略" width="140">
            <template #default="{ row }">{{ row.retryPolicy }}</template>
          </el-table-column>
          <el-table-column label="说明" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">{{ row.notes || '--' }}</template>
          </el-table-column>
        </AppDataTable>
      </template>
    </AppSectionCard>

    <el-drawer v-model="detailVisible" size="720px" :title="detailTitle" destroy-on-close>
      <div v-if="detailLoading" class="page-state">
        <el-skeleton animated :rows="10" />
      </div>

      <el-result v-else-if="detailError" class="page-state" icon="warning" title="事件详情加载失败" :sub-title="detailError" />

      <template v-else-if="detail?.event">
        <section class="drawer-section">
          <h3>事件摘要</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="事件 ID">{{ detail.event.id }}</el-descriptions-item>
            <el-descriptions-item label="Request ID">{{ detail.event.requestId }}</el-descriptions-item>
            <el-descriptions-item label="事件类型">{{ detail.event.eventType }}</el-descriptions-item>
            <el-descriptions-item label="工作流">{{ detail.event.workflowName || detail.event.workflowHint || '--'
              }}</el-descriptions-item>
            <el-descriptions-item label="工作流版本">{{ detail.event.workflowVersion || '--' }}</el-descriptions-item>
            <el-descriptions-item label="所属域">{{ detail.event.workflowDomain || '--' }}</el-descriptions-item>
            <el-descriptions-item label="负责人">{{ detail.event.workflowOwner || '--' }}</el-descriptions-item>
            <el-descriptions-item label="外部执行 ID">{{ detail.event.externalExecutionId || '--' }}</el-descriptions-item>
            <el-descriptions-item label="送达状态">
              <AppStatusTag :label="deliveryLabel(detail.event.deliveryStatus)"
                :tone="statusTone(detail.event.deliveryStatus)" />
            </el-descriptions-item>
            <el-descriptions-item label="工作流状态">
              <AppStatusTag :label="workflowStatusLabel(detail.event.workflowStatus)"
                :tone="statusTone(detail.event.workflowStatus)" />
            </el-descriptions-item>
            <el-descriptions-item label="人工治理状态">
              <AppStatusTag :label="manualStatusLabel(detail.event.manualStatus)"
                :tone="manualStatusTone(detail.event.manualStatus)" />
            </el-descriptions-item>
            <el-descriptions-item label="人工原因">{{ detail.event.manualReason || '--' }}</el-descriptions-item>
            <el-descriptions-item label="HTTP 状态">{{ detail.event.statusCode ?? '--' }}</el-descriptions-item>
            <el-descriptions-item label="尝试次数">{{ detail.event.attemptCount }} / {{ detail.event.maxAttempts
              }}</el-descriptions-item>
            <el-descriptions-item label="处理耗时">{{ detail.event.workflowDurationMs ? `${detail.event.workflowDurationMs}
              ms` : '--' }}</el-descriptions-item>
            <el-descriptions-item label="回调时间">{{ detail.event.lastCallbackAt ?
              formatDateTime(detail.event.lastCallbackAt) : '--' }}</el-descriptions-item>
            <el-descriptions-item label="错误代码">{{ detail.event.errorCode || '--' }}</el-descriptions-item>
            <el-descriptions-item label="错误摘要">{{ detail.event.workflowErrorSummary || detail.event.errorMessage || '--'
              }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="drawer-section">
          <h3>事件时间线</h3>
          <el-timeline>
            <el-timeline-item timestamp="创建" :type="timelineType(detail.event.deliveryStatus)">
              <div class="timeline-entry">
                <strong>{{ formatDateTime(detail.event.createdAt) }}</strong>
                <span>事件已创建并进入 n8n 投递链路。</span>
              </div>
            </el-timeline-item>
            <el-timeline-item v-if="detail.event.lastCallbackAt" timestamp="回调写回"
              :type="timelineType(detail.event.workflowStatus)">
              <div class="timeline-entry">
                <strong>{{ formatDateTime(detail.event.lastCallbackAt) }}</strong>
                <span>
                  {{ workflowStatusLabel(detail.event.workflowStatus) }}
                  <template v-if="detail.event.externalExecutionId"> / 执行 ID {{ detail.event.externalExecutionId
                    }}</template>
                </span>
              </div>
            </el-timeline-item>
            <el-timeline-item v-for="item in detail.replayEvents" :key="item.id" timestamp="人工重发" type="primary">
              <div class="timeline-entry">
                <strong>{{ formatDateTime(item.createdAt) }}</strong>
                <span>派生事件 #{{ item.id }}，送达 {{ deliveryLabel(item.deliveryStatus) }}，工作流 {{
                  workflowStatusLabel(item.workflowStatus) }}</span>
              </div>
            </el-timeline-item>
          </el-timeline>
        </section>

        <section v-if="detail.sourceEvent" class="drawer-section">
          <h3>来源事件</h3>
          <div class="linked-card">
            <div class="two-line-cell">
              <strong>#{{ detail.sourceEvent.id }} / {{ detail.sourceEvent.eventType }}</strong>
              <span>{{ detail.sourceEvent.requestId }}</span>
            </div>
            <div class="inline-tags">
              <AppStatusTag :label="deliveryLabel(detail.sourceEvent.deliveryStatus)"
                :tone="statusTone(detail.sourceEvent.deliveryStatus)" />
              <AppStatusTag :label="workflowStatusLabel(detail.sourceEvent.workflowStatus)"
                :tone="statusTone(detail.sourceEvent.workflowStatus)" />
            </div>
          </div>
        </section>

        <section v-if="detail.replayEvents.length" class="drawer-section">
          <h3>人工重发链路</h3>
          <div class="linked-list">
            <article v-for="item in detail.replayEvents" :key="item.id" class="linked-card">
              <div class="two-line-cell">
                <strong>#{{ item.id }} / {{ item.eventType }}</strong>
                <span>{{ formatDateTime(item.createdAt) }}</span>
              </div>
              <div class="inline-tags">
                <AppStatusTag :label="deliveryLabel(item.deliveryStatus)" :tone="statusTone(item.deliveryStatus)" />
                <AppStatusTag :label="workflowStatusLabel(item.workflowStatus)"
                  :tone="statusTone(item.workflowStatus)" />
              </div>
            </article>
          </div>
        </section>

        <section v-if="detail.event.callbackPayloadJson" class="drawer-section">
          <h3>回调载荷</h3>
          <pre class="payload-preview">{{ formatJson(detail.event.callbackPayloadJson) }}</pre>
        </section>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAiInfrastructureN8nEventDetail,
  getAiInfrastructureN8nEvents,
  getAiInfrastructureOverview,
  manualHandoffAiInfrastructureN8nEvent,
  retryAiInfrastructureN8nEvent,
  type AdminAiInfrastructureOverviewView,
  type AdminAiN8nEventDetailView,
  type AdminAiN8nEventView,
  type AdminAiProviderRecentErrorView,
} from '@/api/admin'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppFilterBar from '@/components/common/AppFilterBar.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import CompactPager from '@/components/common/CompactPager.vue'
import { formatDateTime, resolveErrorMessage } from '@/utils/formatters'

const pageNum = ref(1)
const pageSize = 20
const total = ref(0)
const records = ref<AdminAiN8nEventView[]>([])
const overview = ref<AdminAiInfrastructureOverviewView>()
const detail = ref<AdminAiN8nEventDetailView>()
const detailVisible = ref(false)

const overviewLoading = ref(true)
const eventsLoading = ref(true)
const detailLoading = ref(false)
const overviewError = ref('')
const eventsError = ref('')
const detailError = ref('')
const retryingId = ref<number>()
const handingOffId = ref<number>()

const filters = reactive({
  q: '',
  deliveryStatus: '',
  workflowStatus: '',
  manualStatus: '',
  eventType: '',
  createdDate: '',
})

const normalizeStatus = (value?: string) => String(value || '').trim().toUpperCase()

const statusLabel = (value?: string) =>
({
  UP: '正常',
  DOWN: '不可用',
  DEGRADED: '降级',
  DELIVERED: '送达成功',
  FAILED: '失败',
  PENDING: '待处理',
  SKIPPED: '已跳过',
  NOT_STARTED: '未启动',
  RUNNING: '运行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  SUCCESS: '成功',
}[normalizeStatus(value)] || value || '--')

const statusTone = (value?: string) =>
  ({
    UP: 'success',
    SUCCESS: 'success',
    DELIVERED: 'success',
    COMPLETED: 'success',
    RUNNING: 'primary',
    DEGRADED: 'warning',
    PENDING: 'warning',
    DOWN: 'danger',
    FAILED: 'danger',
    CANCELLED: 'warning',
    NOT_STARTED: 'info',
    SKIPPED: 'info',
  }[normalizeStatus(value)] || 'info') as 'primary' | 'success' | 'warning' | 'danger' | 'info'

const roleLabel = (value?: string) =>
({
  CHAT: 'Chat 路由',
  EMBEDDING: 'Embedding 路由',
}[normalizeStatus(value)] || value || '--')

const deliveryLabel = (value?: string) =>
({
  DELIVERED: '送达成功',
  FAILED: '送达失败',
  PENDING: '待处理',
  SKIPPED: '已跳过',
}[normalizeStatus(value)] || value || '--')

const workflowStatusLabel = (value?: string) =>
({
  NOT_STARTED: '未启动',
  PENDING: '待处理',
  RUNNING: '运行中',
  COMPLETED: '已完成',
  FAILED: '执行失败',
  CANCELLED: '人工中止 / 已取消',
}[normalizeStatus(value)] || value || '--')

const manualStatusLabel = (value?: string) =>
({
  PENDING: '待人工处理',
  RESOLVED: '已人工闭环',
}[normalizeStatus(value)] || '未介入')

const manualStatusTone = (value?: string) =>
  ({
    PENDING: 'warning',
    RESOLVED: 'success',
  }[normalizeStatus(value)] || 'info') as 'primary' | 'success' | 'warning' | 'danger' | 'info'

const timelineType = (value?: string) =>
  ({
    DELIVERED: 'success',
    COMPLETED: 'success',
    FAILED: 'danger',
    CANCELLED: 'warning',
    PENDING: 'warning',
    RUNNING: 'primary',
  }[normalizeStatus(value)] || 'info') as 'primary' | 'success' | 'warning' | 'danger' | 'info'

const formatPercent = (value?: number) => `${Number(value || 0).toFixed(1)}%`

const formatRecentErrors = (items: AdminAiProviderRecentErrorView[] = []) =>
  items.length ? items.map((item) => `${item.errorCode}×${item.count}`).join('，') : '--'

const workflowMetaLabel = (row: AdminAiN8nEventView) => {
  const parts = [row.workflowVersion, row.workflowDomain, row.workflowOwner].filter(Boolean)
  return parts.length ? parts.join(' / ') : row.workflowHint || '--'
}

const allowManualHandoff = (row: AdminAiN8nEventView) =>
  ['FAILED', 'SKIPPED'].includes(normalizeStatus(row.deliveryStatus))
  || ['FAILED', 'CANCELLED', 'NOT_STARTED'].includes(normalizeStatus(row.workflowStatus))

const defaultProviderLabel = computed(() => {
  const providers = overview.value?.providers
  if (!providers) {
    return '--'
  }
  return `${providers.defaultProviderCode || '--'} / ${providers.defaultModelCode || '--'}`
})

const overviewWarnings = computed(() => overview.value?.warnings || [])

const infrastructureWarningTitle = (value?: string) =>
({
  SERVICE_HEALTH: 'AI 服务健康',
  PROVIDER_HEALTH: 'Provider 健康',
  N8N_SUMMARY: 'n8n 概览',
  FILE_CAPABILITIES: '文件解析能力',
}[normalizeStatus(value)] || value || '基础设施子项')

const detailTitle = computed(() => {
  if (!detail.value?.event) {
    return '事件详情'
  }
  return `事件详情 #${detail.value.event.id}`
})

const loadOverview = async () => {
  overviewLoading.value = true
  overviewError.value = ''
  try {
    overview.value = await getAiInfrastructureOverview()
  } catch (error) {
    overviewError.value = resolveErrorMessage(error, 'AI 基础设施概览加载失败，请稍后重试')
  } finally {
    overviewLoading.value = false
  }
}

const loadEvents = async () => {
  eventsLoading.value = true
  eventsError.value = ''
  try {
    const page = await getAiInfrastructureN8nEvents({
      pageNum: pageNum.value,
      pageSize,
      q: filters.q || undefined,
      deliveryStatus: filters.deliveryStatus || undefined,
      workflowStatus: filters.workflowStatus || undefined,
      manualStatus: filters.manualStatus || undefined,
      eventType: filters.eventType || undefined,
      createdDate: filters.createdDate || undefined,
    })
    records.value = page.records
    total.value = page.total
  } catch (error) {
    eventsError.value = resolveErrorMessage(error, 'n8n 事件加载失败，请稍后重试')
  } finally {
    eventsLoading.value = false
  }
}

const refreshN8n = async () => {
  await Promise.all([loadOverview(), loadEvents()])
}

const handleSearch = async () => {
  pageNum.value = 1
  await loadEvents()
}

const handlePageChange = async (value: number) => {
  pageNum.value = value
  await loadEvents()
}

const resetFilters = async () => {
  filters.q = ''
  filters.deliveryStatus = ''
  filters.workflowStatus = ''
  filters.manualStatus = ''
  filters.eventType = ''
  filters.createdDate = ''
  pageNum.value = 1
  await loadEvents()
}

const openDetail = async (row: AdminAiN8nEventView) => {
  detailVisible.value = true
  detailLoading.value = true
  detailError.value = ''
  detail.value = undefined
  try {
    detail.value = await getAiInfrastructureN8nEventDetail(row.id)
  } catch (error) {
    detailError.value = resolveErrorMessage(error, '事件详情加载失败，请稍后重试')
  } finally {
    detailLoading.value = false
  }
}

const handleRetry = async (row: AdminAiN8nEventView) => {
  if (!row.workflowHint) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `将对事件 #${row.id} 发起人工重发，沿用原始幂等键和工作流路由。`,
      '确认人工重发',
      {
        type: 'warning',
        confirmButtonText: '确认重发',
        cancelButtonText: '取消',
      },
    )
    retryingId.value = row.id
    const result = await retryAiInfrastructureN8nEvent(row.id)
    ElMessage.success(`已发起人工重发，新事件 #${result.event.id} 当前状态：${deliveryLabel(result.event.deliveryStatus)}`)
    await Promise.all([loadOverview(), loadEvents()])
    if (detailVisible.value && detail.value?.event.id === row.id) {
      await openDetail(row)
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(resolveErrorMessage(error, 'n8n 事件人工重发失败，请稍后重试'))
    }
  } finally {
    retryingId.value = undefined
  }
}

const handleManualHandoff = async (row: AdminAiN8nEventView) => {
  if (!allowManualHandoff(row)) {
    return
  }
  try {
    const result = await ElMessageBox.prompt(
      `请输入事件 #${row.id} 的转人工原因。`,
      '转人工处理',
      {
        inputPlaceholder: '例如：非重试型失败，需要人工核查 workflow 配置',
        confirmButtonText: '确认转人工',
        cancelButtonText: '取消',
        inputValue: row.manualReason || '',
      },
    )
    handingOffId.value = row.id
    await manualHandoffAiInfrastructureN8nEvent(row.id, result.value)
    ElMessage.success('已转入人工处理队列')
    await Promise.all([loadOverview(), loadEvents()])
    if (detailVisible.value && detail.value?.event.id === row.id) {
      await openDetail(row)
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(resolveErrorMessage(error, '转人工处理失败，请稍后重试'))
    }
  } finally {
    handingOffId.value = undefined
  }
}

const formatJson = (value?: string) => {
  if (!value) {
    return '--'
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

onMounted(async () => {
  await Promise.all([loadOverview(), loadEvents()])
})
</script>

<style scoped>
.ai-infrastructure-page {
  gap: 18px;
}

.page-state {
  padding: 24px;
}

.warning-stack {
  display: grid;
  gap: 12px;
}

.summary-grid,
.provider-grid,
.n8n-metrics-grid,
.insight-grid,
.runtime-grid {
  display: grid;
  gap: 16px;
}

.summary-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.provider-grid,
.runtime-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  padding: 20px 24px 12px;
}

.n8n-metrics-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
  padding: 20px 24px 0;
}

.insight-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  padding: 12px 24px 20px;
}

.summary-card,
.provider-card,
.metric-card,
.insight-panel,
.runtime-card,
.linked-card {
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid rgba(192, 199, 212, 0.26);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}

.summary-card,
.metric-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
}

.summary-card .material-symbols-outlined {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: rgba(0, 96, 169, 0.08);
  color: #0060a9;
  font-size: 22px;
}

.summary-card strong,
.metric-card strong {
  display: block;
  color: #101828;
  font-size: 24px;
  font-weight: 800;
  line-height: 1.1;
}

.summary-card span:last-child,
.metric-card span:last-child,
.section-meta,
.table-count,
.inline-error-tip {
  color: #667085;
  font-size: 12px;
}

.summary-card.is-loading {
  min-height: 96px;
}

.metric-card.success strong {
  color: #067647;
}

.metric-card.danger strong {
  color: #b42318;
}

.metric-card.warning strong {
  color: #b54708;
}

.metric-card.info strong {
  color: #175cd3;
}

.section-actions,
.row-actions,
.inline-tags,
.tag-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.row-actions,
.tag-row,
.inline-tags {
  flex-wrap: wrap;
}

.status-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 20px 24px 0;
}

.status-chip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid rgba(192, 199, 212, 0.18);
}

.status-chip span {
  color: #667085;
  font-size: 13px;
}

.status-chip strong {
  color: #101828;
  font-size: 14px;
  font-weight: 700;
}

.provider-card,
.runtime-card,
.insight-panel {
  padding: 18px 20px;
}

.provider-card__header,
.runtime-card__header,
.insight-panel header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.provider-card__eyebrow {
  color: #667085;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
}

.provider-card h3,
.runtime-card h3,
.insight-panel h3,
.drawer-section h3 {
  margin: 4px 0 0;
  color: #101828;
  font-size: 18px;
  font-weight: 800;
}

.provider-card__details {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 18px 0 14px;
}

.provider-card__details dt,
.runtime-card__meta span,
.insight-stat-list label {
  margin-bottom: 4px;
  color: #667085;
  font-size: 12px;
}

.provider-card__details dd,
.runtime-card__meta strong,
.provider-card__footer span,
.insight-stat-list strong {
  margin: 0;
  color: #101828;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}

.provider-card__footer {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: #667085;
  font-size: 12px;
}

.alert-box {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 14px;
  font-size: 12px;
}

.alert-box.danger {
  background: rgba(217, 45, 32, 0.06);
  color: #b42318;
}

.alert-box strong {
  display: block;
  margin-bottom: 4px;
}

.table-shell,
.table-footer {
  padding: 0 24px 24px;
}

.table-shell {
  padding-top: 18px;
}

.two-line-cell,
.timeline-entry {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.two-line-cell strong,
.timeline-entry strong {
  color: #101828;
  font-size: 14px;
  font-weight: 700;
}

.two-line-cell span,
.timeline-entry span,
.latest-failure-card p,
.runtime-card__copy {
  color: #667085;
  font-size: 12px;
  line-height: 1.6;
  word-break: break-word;
}

.insight-panel {
  min-height: 240px;
}

.insight-stat-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 18px 0 14px;
}

.latest-failure-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.runtime-card__copy {
  margin: 14px 0 18px;
  color: #475467;
  font-size: 13px;
}

.runtime-card__meta strong {
  display: block;
}

.inline-error-tip {
  padding: 0 24px 10px;
}

.table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid rgba(192, 199, 212, 0.18);
  padding-top: 18px;
}

.drawer-section {
  margin-bottom: 24px;
}

.drawer-section :deep(.el-descriptions) {
  margin-top: 14px;
}

.linked-list {
  display: grid;
  gap: 12px;
}

.linked-card {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
}

.payload-preview {
  margin: 12px 0 0;
  padding: 16px;
  border-radius: 16px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.7;
  overflow: auto;
}

@media (max-width: 1280px) {

  .summary-grid,
  .n8n-metrics-grid,
  .status-strip,
  .provider-grid,
  .runtime-grid,
  .insight-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {

  .provider-card__details,
  .insight-stat-list {
    grid-template-columns: 1fr;
  }

  .provider-card__footer,
  .table-footer,
  .section-actions,
  .linked-card {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
