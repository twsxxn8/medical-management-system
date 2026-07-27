<template>
  <div class="monitor-page">
    <h2 class="page-title">
      <el-icon><Monitor /></el-icon>
      系统监控
    </h2>

    <!-- 健康状态卡片 -->
    <el-row :gutter="16" class="card-row">
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>系统健康</span>
              <el-tag :type="healthStatus === 'UP' ? 'success' : 'danger'" effect="dark">
                {{ healthStatus }}
              </el-tag>
            </div>
          </template>
          <div class="health-details" v-if="healthComponents">
            <div class="health-item">
              <span class="label">数据库</span>
              <el-tag
                :type="(healthComponents.database || '').includes('UP') ? 'success' : 'danger'"
                size="small"
              >
                {{ healthComponents.database || '-' }}
              </el-tag>
            </div>
            <div class="health-item">
              <span class="label">Redis</span>
              <el-tag
                :type="(healthComponents.redis || '').includes('UP') ? 'success' : 'danger'"
                size="small"
              >
                {{ healthComponents.redis || '-' }}
              </el-tag>
            </div>
          </div>
          <div v-else class="loading-text">加载中...</div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>AI 服务状态</span>
              <el-tag
                :type="aiStatus === 'HEALTHY' ? 'success' : 'warning'"
                effect="dark"
              >
                {{ aiStatus }}
              </el-tag>
            </div>
          </template>
          <div v-if="aiProviders.length">
            <div v-for="p in aiProviders" :key="p.name" class="provider-item">
              <span class="label">{{ p.name }}</span>
              <span class="value">{{ p.model }}</span>
              <el-tag size="small" type="info">P{{ p.priority }}</el-tag>
            </div>
          </div>
          <div v-else class="loading-text">加载中...</div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>断路器概览</span>
              <span class="count-badge">{{ breakers.length }}</span>
            </div>
          </template>
          <div v-if="breakers.length">
            <div v-for="b in breakers.slice(0, 4)" :key="b.name" class="breaker-item">
              <span class="label">{{ b.name }}</span>
              <el-tag
                :type="b.state === 'CLOSED' ? 'success' : b.state === 'HALF_OPEN' ? 'warning' : 'danger'"
                size="small"
              >
                {{ b.state }}
              </el-tag>
            </div>
          </div>
          <div v-else class="loading-text">加载中...</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 断路器详情表格 -->
    <el-card shadow="hover" class="table-card">
      <template #header>
        <div class="card-header">
          <span>断路器详情</span>
          <el-button type="primary" link size="small" @click="loadAll">刷新</el-button>
        </div>
      </template>
      <el-table :data="breakers" stripe size="small" empty-text="暂无数据">
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="state" label="状态" width="110">
          <template #default="{ row }">
            <el-tag
              :type="row.state === 'CLOSED' ? 'success' : row.state === 'HALF_OPEN' ? 'warning' : 'danger'"
              size="small"
              effect="dark"
            >
              {{ row.state }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failureRate" label="失败率" width="90" align="center" />
        <el-table-column prop="slowCallRate" label="慢调用率" width="90" align="center" />
        <el-table-column prop="numberOfSuccessfulCalls" label="成功数" width="90" align="center" />
        <el-table-column prop="numberOfFailedCalls" label="失败数" width="90" align="center" />
        <el-table-column prop="numberOfSlowCalls" label="慢调用数" width="90" align="center" />
        <el-table-column prop="numberOfNotPermittedCalls" label="拒绝数" width="90" align="center" />
      </el-table>
    </el-card>

    <!-- 外部监控端点 -->
    <el-row :gutter="16" class="card-row">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>Actuator 端点</span>
          </template>
          <div class="endpoint-list">
            <a href="/actuator/health" target="_blank" class="endpoint-link">
              /actuator/health → 健康详情
            </a>
            <a href="/actuator/metrics" target="_blank" class="endpoint-link">
              /actuator/metrics → 全量指标
            </a>
            <a href="/actuator/prometheus" target="_blank" class="endpoint-link">
              /actuator/prometheus → Prometheus 格式
            </a>
            <a href="/actuator/circuitbreakers" target="_blank" class="endpoint-link">
              /actuator/circuitbreakers → 断路器列表
            </a>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>关于监控</span>
          </template>
          <div class="about-text">
            <p><strong>Metrics</strong> — 通过 Micrometer 采集，Prometheus 格式暴露</p>
            <p><strong>Health</strong> — 复合健康检查，含 DB / Redis 连接状态</p>
            <p><strong>CircuitBreakers</strong> — Resilience4j 断路器实时状态</p>
            <p><strong>日志</strong> — SLF4J + Logback，异常自动记录完整堆栈</p>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { Monitor } from "@element-plus/icons-vue";
import { monitorApi, type CircuitBreakerInfo, type ProviderInfo } from "../api/monitor";

const healthStatus = ref("...");
const healthComponents = ref<Record<string, any> | null>(null);
const aiStatus = ref("...");
const aiProviders = ref<ProviderInfo[]>([]);
const breakers = ref<CircuitBreakerInfo[]>([]);

async function loadAll() {
  try {
    const [health, stats, cbList] = await Promise.all([
      monitorApi.getHealth(),
      monitorApi.getAiStats(),
      monitorApi.getCircuitBreakers()
    ]);
    healthStatus.value = (health.status || "").toUpperCase();
    healthComponents.value = health.components || null;
    aiStatus.value = stats.overallStatus;
    aiProviders.value = stats.providers || [];
    breakers.value = cbList;
  } catch (e: any) {
    console.error("监控数据加载失败:", e);
  }
}

onMounted(loadAll);
</script>

<style scoped>
.monitor-page {
  padding: 20px;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  font-size: 20px;
  color: #303133;
}

.card-row {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.count-badge {
  background: #409eff;
  color: #fff;
  border-radius: 10px;
  padding: 0 8px;
  font-size: 12px;
}

.health-details {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.health-item, .provider-item, .breaker-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid #f0f0f0;
}

.health-item:last-child, .provider-item:last-child, .breaker-item:last-child {
  border-bottom: none;
}

.label {
  color: #606266;
  font-size: 13px;
}

.value {
  color: #909399;
  font-size: 12px;
}

.loading-text {
  text-align: center;
  padding: 20px;
  color: #909399;
}

.table-card {
  margin-bottom: 16px;
}

.endpoint-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.endpoint-link {
  display: block;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  color: #409eff;
  text-decoration: none;
  font-size: 13px;
  transition: background 0.2s;
}

.endpoint-link:hover {
  background: #ecf5ff;
}

.about-text p {
  margin: 6px 0;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
</style>
