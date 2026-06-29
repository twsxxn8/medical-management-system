<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { systemLogsApi, type SystemLog } from "../api/systemLogs";

// 页面状态：列表加载 + 数据源
const loading = ref(false);
const items = ref<SystemLog[]>([]);

// 拉取日志列表（初次进入页面 & 点击刷新）
async function refresh() {
  loading.value = true;
  try {
    items.value = await systemLogsApi.list();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "加载失败");
  } finally {
    loading.value = false;
  }
}

// 进入页面自动加载列表
onMounted(refresh);
</script>

<template>
  <section>
    <!-- 顶部栏：标题 + 刷新按钮 -->
    <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
      <h2 style="margin: 0">系统操作日志</h2>
      <el-button type="primary" @click="refresh">刷新</el-button>
    </div>

    <!-- 列表：对应后端 system_log 表 -->
    <el-table v-loading="loading" :data="items" style="width: 100%; margin-top: 14px" border>
      <el-table-column type="index" label="序号" width="60" />
      <el-table-column prop="userId" label="操作人 ID" width="110" />
      <el-table-column prop="operation" label="操作内容" min-width="160" />
      <el-table-column prop="method" label="请求方法" width="140" />
      <el-table-column prop="ipAddress" label="IP 地址" width="140" />
      <el-table-column prop="duration" label="耗时 (ms)" width="110" />
      <el-table-column prop="createTime" label="操作时间" width="180" />
    </el-table>

    <!-- 空状态：列表无数据时显示 -->
    <el-empty v-if="!loading && items.length === 0" description="暂无操作日志" />
  </section>
</template>
