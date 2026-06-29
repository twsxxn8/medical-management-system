<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Edit, Delete } from "@element-plus/icons-vue";
import { appointmentsApi, type CreateAppointmentPayload, type Appointment } from "../api/appointments";
import { patientsApi, type Patient } from "../api/patients";
import { doctorsApi, type Doctor } from "../api/doctors";
import { departmentsApi, type Department } from "../api/departments";
import { getRole } from "../utils/authStorage";

const loading = ref(false);
const items = ref<Appointment[]>([]);

const userRole = ref(getRole() || "");

const patientLoading = ref(false);
const doctorLoading = ref(false);
const deptLoading = ref(false);
const patients = ref<Patient[]>([]);
const doctors = ref<Doctor[]>([]);
const departments = ref<Department[]>([]);

const filter = reactive<{ patientId: number | null; doctorId: number | null }>({ patientId: null, doctorId: null });

const dialogOpen = ref(false);
const submitting = ref(false);
const editingId = ref<number | null>(null);

const form = reactive<{
  patientId: number | null;
  doctorId: number | null;
  departmentId: number | null;
  appointmentDate: string;
  timeSlot: string;
  reason: string;
  remark: string;
}>({
  patientId: null,
  doctorId: null,
  departmentId: null,
  appointmentDate: "",
  timeSlot: "",
  reason: "",
  remark: ""
});

// 时段选项
const timeSlots = [
  { label: "上午 (08:00-12:00)", value: "上午" },
  { label: "下午 (14:00-18:00)", value: "下午" }
];

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

// 根据科室过滤医生
const filteredDoctors = computed(() => {
  if (!form.departmentId) return [];
  return doctors.value.filter(d => d.departmentId === form.departmentId);
});

const deptNameById = computed(() => {
  const map = new Map<number, string>();
  for (const d of departments.value) map.set(d.id, d.name);
  return map;
});

async function loadDropdowns() {
  patientLoading.value = true;
  doctorLoading.value = true;
  deptLoading.value = true;
  try {
    const [patientData, doctorData, deptData] = await Promise.all([
      patientsApi.list(),
      doctorsApi.list(),
      departmentsApi.list()
    ]);
    patients.value = patientData;
    doctors.value = doctorData;
    departments.value = deptData.content ?? [];
  } catch (e: any) {
    ElMessage.error(e?.message ?? "下拉数据加载失败");
  } finally {
    patientLoading.value = false;
    doctorLoading.value = false;
    deptLoading.value = false;
  }
}

async function refresh() {
  loading.value = true;
  try {
    const params: any = {};
    if (filter.patientId) params.patientId = filter.patientId;
    if (filter.doctorId) params.doctorId = filter.doctorId;
    items.value = await appointmentsApi.list(Object.keys(params).length ? params : undefined);
  } catch (e: any) {
    ElMessage.error(e?.message ?? "加载失败");
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingId.value = null;
  form.patientId = null;
  form.doctorId = null;
  form.departmentId = null;
  form.appointmentDate = new Date().toISOString().slice(0, 10);
  form.timeSlot = "";
  form.reason = "";
  form.remark = "";
  dialogOpen.value = true;
}

function openEdit(row: Appointment) {
  editingId.value = row.id;
  form.patientId = row.patientId;
  form.doctorId = row.doctorId;
  form.departmentId = row.departmentId;
  form.appointmentDate = row.appointmentDate.slice(0, 10);
  form.timeSlot = row.timeSlot ?? "";
  form.reason = row.reason ?? "";
  form.remark = row.remark ?? "";
  dialogOpen.value = true;
}

async function submit() {
  if (!form.patientId || !form.doctorId || !form.departmentId) {
    ElMessage.warning("请选择患者、医生和科室");
    return;
  }
  if (!form.appointmentDate || !form.timeSlot?.trim()) {
    ElMessage.warning("请选择预约日期和时段");
    return;
  }

  submitting.value = true;
  try {
    if (!editingId.value) {
      const payload: CreateAppointmentPayload = {
        patientId: form.patientId,
        doctorId: form.doctorId,
        departmentId: form.departmentId,
        appointmentDate: form.appointmentDate,
        timeSlot: form.timeSlot.trim(),
        reason: form.reason?.trim() || "",
        remark: form.remark?.trim() || ""
      };
      await appointmentsApi.create(payload);
      ElMessage.success("新增成功");
    } else {
      await appointmentsApi.update(editingId.value, {
        patientId: form.patientId,
        doctorId: form.doctorId,
        departmentId: form.departmentId,
        appointmentDate: form.appointmentDate,
        timeSlot: form.timeSlot.trim(),
        reason: form.reason?.trim() || "",
        remark: form.remark?.trim() || ""
      });
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

async function updateStatus(row: Appointment, status: number) {
  const old = row.status;
  row.status = status;
  try {
    await appointmentsApi.updateStatus(row.id, status);
    ElMessage.success("状态已更新");
  } catch (e: any) {
    row.status = old;
    ElMessage.error(e?.message ?? "状态更新失败");
  }
}

function getStatusType(status: number) {
  const map: Record<number, string> = { 0: "warning", 1: "primary", 2: "success", 3: "info" };
  return map[status] ?? "info";
}

function getStatusText(status: number) {
  const map: Record<number, string> = { 0: "待确认", 1: "已确认", 2: "已完成", 3: "已取消" };
  return map[status] ?? "未知";
}

// 限制日期范围：只能选择明天到一个月后
function disabledDate(time: Date) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const tomorrow = new Date(today);
  tomorrow.setDate(tomorrow.getDate() + 1);

  const maxDate = new Date(today);
  maxDate.setMonth(maxDate.getMonth() + 1);

  return time.getTime() < tomorrow.getTime() || time.getTime() > maxDate.getTime();
}

// 检查时段是否已满（最多3个）
function isTimeSlotFull(timeSlot: string): boolean {
  if (!form.doctorId || !form.appointmentDate) return false;

  const count = items.value.filter(
    item =>
      item.doctorId === form.doctorId &&
      item.appointmentDate === form.appointmentDate &&
      item.timeSlot === timeSlot &&
      item.status !== 3
  ).length;

  return count >= 3;
}

// 科室切换时清空医生选择
function handleDepartmentChange() {
  form.doctorId = null;
}

/**
 * 删除预约：二次确认后调用删除接口（仅管理员可见）。
 */
async function handleDelete(row: Appointment) {
  try {
    await ElMessageBox.confirm(`确认删除该预约记录吗？`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning"
    });
    await appointmentsApi.delete(row.id);
    ElMessage.success("删除成功");
    await refresh();
  } catch (e: any) {
    if (e !== "cancel") {
      ElMessage.error(e?.message ?? "删除失败");
    }
  }
}

onMounted(async () => {
  await loadDropdowns();
  await refresh();
});
</script>

<template>
  <section>
    <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
      <h2 style="margin: 0">预约管理</h2>
      <div style="display: flex; gap: 10px; align-items: center">
        <el-select v-model="filter.patientId" placeholder="按患者筛选" clearable filterable :loading="patientLoading" style="width: 160px" @change="refresh" @clear="refresh">
          <el-option v-for="p in patients" :key="p.id" :label="p.idCard ?? `患者${p.id}`" :value="p.id" />
        </el-select>
        <el-select v-model="filter.doctorId" placeholder="按医生筛选" clearable filterable :loading="doctorLoading" style="width: 160px" @change="refresh" @clear="refresh">
          <el-option v-for="d in doctors" :key="d.id" :label="d.realName ?? `医生${d.id}`" :value="d.id" />
        </el-select>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增预约</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="items" style="width: 100%; margin-top: 14px" border>
      <el-table-column type="index" label="序号" width="60" />
      <el-table-column label="患者" width="140">
        <template #default="{ row }">{{ patientNameById.get(row.patientId) ?? row.patientId }}</template>
      </el-table-column>
      <el-table-column label="医生" width="120">
        <template #default="{ row }">{{ doctorNameById.get(row.doctorId) ?? row.doctorId }}</template>
      </el-table-column>
      <el-table-column label="科室" width="120">
        <template #default="{ row }">{{ deptNameById.get(row.departmentId) ?? row.departmentId }}</template>
      </el-table-column>
      <el-table-column prop="appointmentDate" label="预约日期" width="130" />
      <el-table-column prop="timeSlot" label="时段" width="100" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button size="small" :icon="Edit" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="success" @click="updateStatus(row, 1)" v-if="row.status === 0">确认</el-button>
          <el-button size="small" type="info" @click="updateStatus(row, 3)" v-if="row.status !== 3">取消</el-button>
          <el-button v-if="userRole === 'ADMIN'" size="small" type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && items.length === 0" description="暂无预约数据" />

    <el-dialog v-model="dialogOpen" :title="editingId ? '编辑预约' : '新增预约'" width="640px">
      <el-form label-width="100px">
        <el-form-item label="患者" required>
          <el-select v-model="form.patientId" filterable placeholder="请选择患者" style="width: 100%">
            <el-option v-for="p in patients" :key="p.id" :label="p.idCard ?? `患者${p.id}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="科室" required>
          <el-select v-model="form.departmentId" filterable placeholder="请选择科室" style="width: 100%" @change="handleDepartmentChange">
            <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="医生" required>
          <el-select v-model="form.doctorId" filterable placeholder="请先选择科室" style="width: 100%" :disabled="!form.departmentId">
            <el-option v-for="d in filteredDoctors" :key="d.id" :label="d.realName ?? `医生${d.id}`" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="预约日期" required>
          <el-date-picker
            v-model="form.appointmentDate"
            type="date"
            placeholder="选择日期"
            style="width: 100%"
            :disabled-date="disabledDate"
          />
        </el-form-item>
        <el-form-item label="时段" required>
          <el-select v-model="form.timeSlot" placeholder="请选择时段" style="width: 100%">
            <el-option
              v-for="slot in timeSlots"
              :key="slot.value"
              :label="slot.label"
              :value="slot.value"
              :disabled="isTimeSlotFull(slot.value)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="就诊原因">
          <el-input v-model="form.reason" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>

