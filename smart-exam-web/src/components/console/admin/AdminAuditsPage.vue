<script setup>
import { inject } from 'vue'
import { prettyJson } from '../../../composables/useAsyncAction'
import { ADMIN_CONSOLE_KEY } from '../../../composables/useAdminConsole'

const admin = inject(ADMIN_CONSOLE_KEY)

const handleAuditPageChange = (value) => {
  admin.auditQuery.page = value
  admin.loadAudits()
}
</script>

<template>
  <section v-if="admin" class="console-block">
    <div class="block-head">
      <div>
        <h3 class="block-title">审计日志检索</h3>
        <p class="block-sub">按操作人、动作、对象和时间窗口查询管理操作记录。</p>
      </div>
      <el-button :loading="admin.loading.audits" @click="admin.loadAudits">刷新日志</el-button>
    </div>

    <div class="form-grid cols-3">
      <el-input v-model="admin.auditQuery.operatorId" placeholder="操作人 ID" />
      <el-input v-model="admin.auditQuery.action" placeholder="动作，如 USER_STATUS_UPDATED" />
      <el-input v-model="admin.auditQuery.targetType" placeholder="对象类型，如 SYS_USER" />
    </div>

    <el-date-picker
      v-model="admin.auditRange"
      type="datetimerange"
      range-separator="至"
      start-placeholder="开始时间"
      end-placeholder="结束时间"
      value-format="YYYY-MM-DDTHH:mm:ss"
    />

    <div class="action-row">
      <el-button type="primary" :loading="admin.loading.audits" @click="admin.loadAudits">查询日志</el-button>
    </div>

    <el-table :data="admin.auditPage.records" size="small" max-height="360">
      <el-table-column prop="id" label="日志ID" min-width="130" />
      <el-table-column prop="operatorId" label="操作人" min-width="100" />
      <el-table-column prop="action" label="动作" min-width="180" />
      <el-table-column prop="targetType" label="对象" min-width="120" />
      <el-table-column prop="targetId" label="对象ID" min-width="120" />
      <el-table-column prop="createdAt" label="时间" min-width="160" />
    </el-table>

    <el-pagination
      class="pagination-row"
      layout="prev, pager, next, total"
      :total="admin.auditPage.total"
      :page-size="admin.auditQuery.size"
      :current-page="admin.auditQuery.page"
      @current-change="handleAuditPageChange"
    />

    <pre class="json-block">{{ prettyJson(admin.auditPage?.records?.[0] || null) }}</pre>
  </section>
  <section v-else class="console-block">
    <p class="hint-text">管理上下文初始化失败，请返回“管理员总览”后重试。</p>
  </section>
</template>

<style scoped>
.pagination-row {
  margin-top: 10px;
}
</style>
