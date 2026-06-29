<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { Edit } from "@element-plus/icons-vue";
import { doctorsApi, type Doctor, type UpdateDoctorPayload } from "../api/doctors";
import { departmentsApi, type Department } from "../api/departments";
import { getRole } from "../utils/authStorage";

// 页面状态：列表加载 + 数据源
const loading = ref(false);
const items = ref<Doctor[]>([]);

// 科室下拉数据：用于筛选与编辑时选择科室
const deptLoading = ref(false);
const departments = ref<Department[]>([]);

// 顶部筛选：按科室过滤（对应后端 GET /api/doctors?departmentId=...）
const filter = reactive<{ departmentId: number | null }>({ departmentId: null });

// 弹窗与提交状态
const dialogOpen = ref(false);
const submitting = ref(false);

// 编辑模式：null 表示新增；有值表示编辑该 id
const editingId = ref<number | null>(null);

// 表单模型：新增与更新共用；提交时根据 editingId 走不同接口
const form = reactive<{
  userId: number | null;
  departmentId: number | null;
  title: string;
  specialty: string;
  consultationFee: number | null;
  introduction: string;
  schedule: string;
}>({
  userId: null,
  departmentId: null,
  title: "",
  specialty: "",
  consultationFee: null,
  introduction: "",
  schedule: ""
});

// 将科室 id -> 名称映射，列表里展示更直观
const deptNameById = computed(() => {
  const map = new Map<number, string>();
  for (const d of departments.value) map.set(d.id, d.name);
  return map;
});

async function loadDepartments() {
  deptLoading.value = true;
  try {
    const pageData = await departmentsApi.list();
    departments.value = pageData.content ?? [];
  } catch (e: any) {
    ElMessage.error(e?.message ?? "科室加载失败");
  } finally {
    deptLoading.value = false;
  }
}

// 拉取医生列表（初次进入页面 & 新增/更新后刷新）
async function refresh() {
  loading.value = true;
  try {
    items.value = await doctorsApi.list(
      filter.departmentId ? { departmentId: filter.departmentId } : undefined
    );
  } catch (e: any) {
    ElMessage.error(e?.message ?? "加载失败");
  } finally {
    loading.value = false;
  }
}

function openEdit(row: Doctor) {
  // 编辑：回填表单；注意后端 UpdateDoctorRequest 不允许修改 userId
  editingId.value = row.id;
  form.userId = row.userId;
  form.departmentId = row.departmentId;
  form.title = row.title ?? "";
  form.specialty = row.specialty ?? "";
  form.consultationFee = row.consultationFee ?? null;
  form.introduction = row.introduction ?? "";
  form.schedule = row.schedule ?? "";
  dialogOpen.value = true;
}

async function submit() {
  // 最小校验：关键外键必须存在
  if (!form.departmentId) {
    ElMessage.warning("请选择科室");
    return;
  }

  submitting.value = true;
  try {
    // 更新（PUT /api/doctors/{id}）
    const payload: UpdateDoctorPayload = {
      departmentId: form.departmentId,
      title: form.title?.trim() || undefined,
      specialty: form.specialty?.trim() || undefined,
      consultationFee: form.consultationFee ?? null,
      introduction: form.introduction?.trim() || undefined,
      schedule: form.schedule?.trim() || undefined
    };
    await doctorsApi.update(editingId.value!, payload);
    ElMessage.success("保存成功");

    dialogOpen.value = false;
    await refresh();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "提交失败");
  } finally {
    submitting.value = false;
  }
}

async function toggleStatus(row: Doctor, next: boolean) {
  // 状态切换：与后端 UpdateDoctorStatusRequest 对齐（0=停诊，1=应诊）
  const status = next ? 1 : 0;
  const old = row.status;
  row.status = status; // 乐观更新：界面先变，失败再回滚
  try {
    await doctorsApi.updateStatus(row.id, status);
    ElMessage.success("状态已更新");
  } catch (e: any) {
    row.status = old;
    ElMessage.error(e?.message ?? "状态更新失败");
  }
}

function formatStatusText(status: number) {
  return status === 1 ? "应诊" : "停诊";
}

// 从 sessionStorage 获取角色（与登录态一致，按标签页隔离）
const userRole = ref(getRole() || "");

// 进入页面：先拉科室（用于下拉），再拉医生列表
onMounted(async () => {
  await loadDepartments();
  await refresh();
});
</script>

<template>
  <!-- 顶部栏：标题 + 筛选 -->
  <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
    <h2 style="margin: 0">医生管理</h2>
    <div style="display: flex; gap: 10px; align-items: center">
      <!-- 筛选：按科室过滤（调用 refresh 时带 departmentId 参数） -->
      <el-select
        v-model="filter.departmentId"
        placeholder="按科室筛选"
        clearable
        filterable
        :loading="deptLoading"
        style="width: 200px"
        @change="refresh"
        @clear="refresh"
      >
        <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
      </el-select>
    </div>
  </div>

  <!-- 列表：对应后端 doctor 表 -->
  <el-table v-loading="loading" :data="items" style="width: 100%; margin-top: 14px" border>
    <el-table-column type="index" label="序号" width="60" />
    <el-table-column prop="realName" label="姓名" width="120" />
    <el-table-column label="科室" min-width="140">
      <template #default="{ row }">
        {{ deptNameById.get(row.departmentId) ?? row.departmentId }}
      </template>
    </el-table-column>
    <el-table-column prop="title" label="职称" width="140" />
    <el-table-column prop="specialty" label="专长" min-width="160" />
    <el-table-column prop="consultationFee" label="挂号费" width="110" />
    <el-table-column label="状态" width="160">
      <!-- 状态开关：切换时调用 PUT /api/doctors/{id}/status -->
      <template #default="{ row }">
        <el-switch
          :model-value="row.status === 1"
          inline-prompt
          active-text="应诊"
          inactive-text="停诊"
          @change="(val: any) => toggleStatus(row, Boolean(val))"
        />
      </template>
    </el-table-column>
    <el-table-column label="操作" width="120">
      <template #default="{ row }">
        <el-button size="small" :icon="Edit" @click="openEdit(row)">编辑</el-button>
      </template>
    </el-table-column>
  </el-table>

  <!-- 空状态：列表无数据时显示 -->
  <el-empty v-if="!loading && items.length === 0" description="暂无医生数据" />

  <!-- 弹窗：编辑医生信息 -->
  <el-dialog v-model="dialogOpen" title="编辑医生" width="640px">
    <el-form label-width="100px">
      <el-form-item label="用户ID">
        <el-input-number v-model="form.userId" :min="1" :step="1" style="width: 100%" disabled />
      </el-form-item>
      <el-form-item label="科室" required>
        <el-select v-model="form.departmentId" filterable placeholder="请选择科室" style="width: 100%">
          <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="职称">
        <el-input v-model="form.title" placeholder="例如：主治医师" />
      </el-form-item>
      <el-form-item label="专长">
        <el-input v-model="form.specialty" placeholder="例如：心内科" />
      </el-form-item>
      <el-form-item label="挂号费">
        <el-input-number v-model="form.consultationFee" :min="0" :step="1" style="width: 100%" />
      </el-form-item>
      <el-form-item label="简介">
        <el-input v-model="form.introduction" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="排班(JSON)">
        <el-input v-model="form.schedule" type="textarea" :rows="3" placeholder='例如：{"mon":"am"}' />
      </el-form-item>
    </el-form>

    <template #footer>
      <!-- footer：取消/提交（提交时 loading 防止重复点击） -->
      <el-button @click="dialogOpen = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
    </template>
  </el-dialog>
</template>

