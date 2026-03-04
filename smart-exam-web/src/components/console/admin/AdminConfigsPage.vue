<script setup>
import { inject } from 'vue'
import { ADMIN_CONSOLE_KEY } from '../../../composables/useAdminConsole'

const admin = inject(ADMIN_CONSOLE_KEY)
</script>

<template>
  <section v-if="admin" class="console-block">
    <div class="block-head">
      <div>
        <h3 class="block-title">系统配置中心</h3>
        <p class="block-sub">查询、编辑并保存后台配置。</p>
      </div>
      <el-button :loading="admin.loading.configs" @click="admin.loadConfigs">刷新配置</el-button>
    </div>

    <div class="form-grid cols-2">
      <el-input v-model="admin.configQuery.groupKey" placeholder="按分组过滤（如 SYSTEM）" />
      <el-input v-model="admin.configQuery.keyword" placeholder="关键字搜索" />
    </div>

    <div class="action-row">
      <el-button type="primary" :loading="admin.loading.configs" @click="admin.loadConfigs">查询配置</el-button>
    </div>

    <el-table :data="admin.configList" size="small" max-height="260">
      <el-table-column prop="groupKey" label="分组" width="110" />
      <el-table-column prop="configKey" label="键" min-width="170" />
      <el-table-column prop="configValue" label="值" min-width="200" show-overflow-tooltip />
    </el-table>

    <div class="config-form">
      <div class="form-grid cols-2">
        <el-input v-model="admin.configForm.configKey" placeholder="配置键（大写）" />
        <el-input v-model="admin.configForm.groupKey" placeholder="分组，默认 SYSTEM" />
      </div>
      <el-input v-model="admin.configForm.configValue" type="textarea" :rows="3" placeholder="配置值，支持 JSON 字符串" />
      <el-input v-model="admin.configForm.description" placeholder="描述（可选）" />
      <div class="action-row">
        <el-button type="primary" :loading="admin.loading.configUpsert" @click="admin.submitConfig">保存配置</el-button>
      </div>
    </div>
  </section>
  <section v-else class="console-block">
    <p class="hint-text">管理上下文初始化失败，请返回“管理员总览”后重试。</p>
  </section>
</template>

<style scoped>
.config-form {
  margin-top: 10px;
  display: grid;
  gap: 8px;
}
</style>
