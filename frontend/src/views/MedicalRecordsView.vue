<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Delete } from "@element-plus/icons-vue";
import { medicalRecordsApi, type CreateMedicalRecordPayload, type MedicalRecord, type UpdateMedicalRecordPayload } from "../api/medicalRecords";
import { patientsApi, type Patient } from "../api/patients";
import { doctorsApi, type Doctor } from "../api/doctors";
import { getRole } from "../utils/authStorage";

// 页面状态：列表加载 + 数据源
const loading = ref(false);
const items = ref<MedicalRecord[]>([]);

// 下拉数据：用于筛选与编辑时选择患者/医生
const patientLoading = ref(false);
const doctorLoading = ref(false);
const patients = ref<Patient[]>([]);
const doctors = ref<Doctor[]>([]);

// 顶部筛选：按患者或医生过滤（对应后端 GET /api/medical-records?...）
const filter = reactive<{ patientId: number | null; doctorId: number | null }>({ patientId: null, doctorId: null });

// 弹窗与提交状态
const dialogOpen = ref(false);
const submitting = ref(false);

// 编辑模式：null 表示新增；有值表示编辑该 id
const editingId = ref<number | null>(null);

// 表单模型：新增与更新共用；提交时根据 editingId 走不同接口
const form = reactive<{
  patientId: number | null;
  doctorId: number | null;
  diagnosis: string;
  prescription: string;
  symptoms: string;
  visitDate: string;
}>({
  patientId: null,
  doctorId: null,
  diagnosis: "",
  prescription: "",
  symptoms: "",
  visitDate: ""
});

// 已选择的药品清单（用于前端展示）
const selectedMedicines = ref<{ medicineId: number; quantity: number }[]>([]);

// 将 id -> 名称映射，列表里展示更直观
const patientNameById = computed(() => {
  const map = new Map<number, string>();
  for (const p of patients.value) map.set(p.id, p.idCard ?? `患者${p.id}`);
  return map;
});

const doctorNameById = computed(() => {
  const map = new Map<number, string>();
  for (const d of doctors.value) map.set(d.id, d.realName ?? `医生${d.id}`);
  return map;
});

async function loadPatients() {
  patientLoading.value = true;
  try {
    patients.value = await patientsApi.list();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "患者加载失败");
  } finally {
    patientLoading.value = false;
  }
}

async function loadDoctors() {
  doctorLoading.value = true;
  try {
    doctors.value = await doctorsApi.list();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "医生加载失败");
  } finally {
    doctorLoading.value = false;
  }
}

// 拉取病历列表（初次进入页面 & 新增/更新后刷新）
async function refresh() {
  loading.value = true;
  try {
    const params: any = {};
    if (filter.patientId) params.patientId = filter.patientId;
    if (filter.doctorId) params.doctorId = filter.doctorId;
    items.value = await medicalRecordsApi.list(Object.keys(params).length ? params : undefined);
  } catch (e: any) {
    ElMessage.error(e?.message ?? "加载失败");
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  // 新增：清空表单
  editingId.value = null;
  form.patientId = null;
  form.doctorId = null;
  form.diagnosis = "";
  form.prescription = "";
  form.symptoms = "";
  form.visitDate = new Date().toISOString().slice(0, 16);
  selectedMedicines.value = [];
  dialogOpen.value = true;
}

function openEdit(row: MedicalRecord) {
  // 编辑：回填表单
  editingId.value = row.id;
  form.patientId = row.patientId;
  form.doctorId = row.doctorId;
  form.diagnosis = row.diagnosis ?? "";
  form.prescription = row.prescription ?? "";
  form.symptoms = row.symptoms ?? "";
  form.visitDate = row.visitDate.slice(0, 16);

  // 解析已选药品
  try {
    if (row.prescriptionMedicines) {
      selectedMedicines.value = JSON.parse(row.prescriptionMedicines);
    } else {
      selectedMedicines.value = [];
    }
  } catch {
    selectedMedicines.value = [];
  }

  dialogOpen.value = true;
}

// 添加药品
function addMedicine(medicineId: number) {
  if (!medicineId) return;
  const exists = selectedMedicines.value.find(m => m.medicineId === medicineId);
  if (exists) {
    ElMessage.warning("该药品已添加");
    return;
  }
  selectedMedicines.value.push({ medicineId, quantity: 1 });
}

// 删除药品
function removeMedicine(index: number) {
  selectedMedicines.value.splice(index, 1);
}

async function submit() {
  // 最小校验：关键外键和诊断必填
  if (!form.patientId) {
    ElMessage.warning("请选择患者");
    return;
  }
  if (!form.doctorId) {
    ElMessage.warning("请选择医生");
    return;
  }
  if (!form.diagnosis?.trim()) {
    ElMessage.warning("请输入诊断内容");
    return;
  }
  if (!form.visitDate) {
    ElMessage.warning("请选择就诊时间");
    return;
  }

  submitting.value = true;
  try {
    if (!editingId.value) {
      // 新增（POST /api/medical-records）
      const payload: CreateMedicalRecordPayload = {
        patientId: form.patientId,
        doctorId: form.doctorId,
        diagnosis: form.diagnosis.trim(),
        prescription: form.prescription?.trim() || "",
        prescriptionMedicines: selectedMedicines.value.length > 0 ? JSON.stringify(selectedMedicines.value) : "",
        symptoms: form.symptoms?.trim() || "",
        visitDate: form.visitDate
      };
      await medicalRecordsApi.create(payload);
      ElMessage.success("新增成功");
    } else {
      // 更新（PUT /api/medical-records/{id}）
      const payload: UpdateMedicalRecordPayload = {
        diagnosis: form.diagnosis?.trim() || "",
        prescription: form.prescription?.trim() || "",
        prescriptionMedicines: selectedMedicines.value.length > 0 ? JSON.stringify(selectedMedicines.value) : "",
        symptoms: form.symptoms?.trim() || "",
        visitDate: form.visitDate || ""
      };
      await medicalRecordsApi.update(editingId.value, payload);
      ElMessage.success("保存成功");
    }

    dialogOpen.value = false;
    await refresh();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "提交失败");
  } finally {
    submitting.value = false;
  }
}

// 进入页面：先拉患者/医生（用于下拉），再拉病历列表
onMounted(async () => {
  await Promise.all([loadPatients(), loadDoctors()]);
  await refresh();
});

/**
 * 删除病历：二次确认后调用删除接口（仅管理员可见）。
 */
async function handleDelete(row: MedicalRecord) {
  try {
    await ElMessageBox.confirm(`确认删除该病历记录吗？`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning"
    });
    await medicalRecordsApi.delete(row.id);
    ElMessage.success("删除成功");
    await refresh();
  } catch (e: any) {
    if (e !== "cancel") {
      ElMessage.error(e?.message ?? "删除失败");
    }
  }
}

// 从 sessionStorage 获取角色（与登录态一致，按标签页隔离）
const userRole = ref(getRole() || "");
</script>

<template>
  <!-- 顶部栏：标题 + 筛选 + 新增按钮 -->
  <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
    <h2 style="margin: 0">病历管理</h2>
    <div style="display: flex; gap: 10px; align-items: center">
      <!-- 筛选：按患者过滤 -->
      <el-select
        v-model="filter.patientId"
        placeholder="按患者筛选"
        clearable
        filterable
        :loading="patientLoading"
        style="width: 160px"
        @change="refresh"
        @clear="refresh"
      >
        <el-option v-for="p in patients" :key="p.id" :label="p.idCard ?? `患者${p.id}`" :value="p.id" />
      </el-select>

      <!-- 筛选：按医生过滤 -->
      <el-select
        v-model="filter.doctorId"
        placeholder="按医生筛选"
        clearable
        filterable
        :loading="doctorLoading"
        style="width: 160px"
        @change="refresh"
        @clear="refresh"
      >
        <el-option v-for="d in doctors" :key="d.id" :label="d.realName ?? `医生${d.id}`" :value="d.id" />
      </el-select>

      <el-button type="primary" :icon="Plus" @click="openCreate">新增病历</el-button>
    </div>
  </div>

  <!-- 列表：对应后端 medical_record 表 -->
  <el-table v-loading="loading" :data="items" style="width: 100%; margin-top: 14px" border>
    <el-table-column type="index" label="序号" width="60" />
    <el-table-column label="患者" width="120">
      <template #default="{ row }">
        {{ patientNameById.get(row.patientId) ?? row.patientId }}
      </template>
    </el-table-column>
    <el-table-column label="医生" width="120">
      <template #default="{ row }">
        {{ doctorNameById.get(row.doctorId) ?? row.doctorId }}
      </template>
    </el-table-column>
    <el-table-column prop="visitDate" label="就诊时间" width="180" />
    <el-table-column prop="diagnosis" label="诊断" min-width="200" show-overflow-tooltip />
    <el-table-column prop="symptoms" label="症状" min-width="160" show-overflow-tooltip />
    <el-table-column prop="prescription" label="处方" min-width="200" show-overflow-tooltip />
    <el-table-column label="操作" width="200">
      <template #default="{ row }">
        <el-button size="small" @click="openEdit(row)">编辑</el-button>
        <!-- 删除按钮：仅管理员可见 -->
        <el-button v-if="userRole === 'ADMIN'" size="small" type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>

  <!-- 空状态：列表无数据时显示 -->
  <el-empty v-if="!loading && items.length === 0" description="暂无病历数据" />

  <!-- 弹窗：新增/编辑病历（提交时根据 editingId 走 create/update） -->
  <el-dialog v-model="dialogOpen" :title="editingId ? '编辑病历' : '新增病历'" width="640px">
    <el-form label-width="100px">
      <el-form-item label="患者" required>
        <el-select v-model="form.patientId" filterable placeholder="请选择患者" style="width: 100%">
          <el-option v-for="p in patients" :key="p.id" :label="p.idCard ?? `患者${p.id}`" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="医生" required>
        <el-select v-model="form.doctorId" filterable placeholder="请选择医生" style="width: 100%">
          <el-option v-for="d in doctors" :key="d.id" :label="d.realName ?? `医生${d.id}`" :value="d.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="就诊时间" required>
        <el-date-picker v-model="form.visitDate" type="datetime" placeholder="选择日期时间" style="width: 100%" />
      </el-form-item>
      <el-form-item label="症状">
        <el-input v-model="form.symptoms" type="textarea" :rows="2" placeholder="例如：发热、咳嗽、咽痛" />
      </el-form-item>
      <el-form-item label="诊断" required>
        <el-input v-model="form.diagnosis" type="textarea" :rows="3" placeholder="例如：急性上呼吸道感染" />
      </el-form-item>
      <el-form-item label="处方">
        <el-input v-model="form.prescription" type="textarea" :rows="3" placeholder="例如：阿莫西林胶囊 0.5g tid*3天" />
      </el-form-item>
    </el-form>

    <template #footer>
      <!-- footer：取消/提交（提交时 loading 防止重复点击） -->
      <el-button @click="dialogOpen = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
    </template>
  </el-dialog>
</template>
