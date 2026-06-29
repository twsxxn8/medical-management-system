<script setup lang="ts">
import { onMounted, ref } from "vue";
import { markRaw } from "vue";
import { User, UserFilled, Calendar, OfficeBuilding } from "@element-plus/icons-vue";
import { statisticsApi, type StatisticsOverview } from "../api/statistics";

// 统计卡片数据
const stats = ref([
  { title: "总患者数", value: 0, icon: markRaw(User), color: "#409EFF" },
  { title: "总医生数", value: 0, icon: markRaw(UserFilled), color: "#67C23A" },
  { title: "今日预约", value: 0, icon: markRaw(Calendar), color: "#E6A23C" },
  { title: "科室总数", value: 0, icon: markRaw(OfficeBuilding), color: "#909399" }
]);

// 加载统计数据
async function loadStats() {
  try {
    const data: StatisticsOverview = await statisticsApi.getOverview();
    stats.value[0].value = data.totalPatients;
    stats.value[1].value = data.totalDoctors;
    stats.value[2].value = data.todayAppointments;
    stats.value[3].value = data.totalDepartments;
  } catch (e) {
    console.error("加载统计数据失败", e);
  }
}

onMounted(loadStats);
</script>

<template>
  <section class="home-section">
    <h2>系统概览</h2>
    <div class="stats-container">
      <el-card v-for="(item, index) in stats" :key="index" class="stat-card" shadow="hover">
        <div class="stat-content">
          <div class="stat-info">
            <div class="stat-title">{{ item.title }}</div>
            <div class="stat-value">{{ item.value }}</div>
          </div>
          <div class="stat-icon" :style="{ backgroundColor: item.color + '20', color: item.color }">
            <el-icon :size="40"><component :is="item.icon" /></el-icon>
          </div>
        </div>
      </el-card>
    </div>

    <div class="welcome-banner">
      <h3>欢迎使用医疗管理系统</h3>
      <p>本系统涵盖科室、医生、患者、预约、药品及病历的全流程管理。请通过左侧菜单选择功能模块。</p>
    </div>
  </section>
</template>

<style scoped>
.home-section {
  background-color: #fff;
  padding: 20px;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}
.stats-container {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin: 20px 0;
}
.stat-card {
  border: none;
}
.stat-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.stat-title {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}
.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
}
.welcome-banner {
  margin-top: 30px;
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 4px;
  border-left: 4px solid #409EFF;
}
.welcome-banner h3 {
  margin: 0 0 10px;
  color: #303133;
}
.welcome-banner p {
  margin: 0;
  color: #606266;
  line-height: 1.6;
}
</style>
