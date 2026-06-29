<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Edit, Delete } from "@element-plus/icons-vue";
import { patientsApi, type CreatePatientPayload, type Patient, type UpdatePatientPayload } from "../api/patients";
import { getRole } from "../utils/authStorage";

// 页面状态：列表加载 + 数据源
const loading = ref(false);
const items = ref<Patient[]>([]);

// 弹窗与提交状态
const dialogOpen = ref(false);
const submitting = ref(false);

// 编辑模式：null 表示新增；有值表示编辑该 id
const editingId = ref<number | null>(null);

// 表单模型：新增与更新共用
const form = reactive<{
  idCard: string;
  gender: string;
  birthDate: string;
  address: string;
  emergencyContact: string;
  emergencyPhone: string;
  medicalHistory: string;
}>({
  idCard: "",
  gender: "",
  birthDate: "",
  address: "",
  emergencyContact: "",
  emergencyPhone: "",
  medicalHistory: ""
});

// 拉取列表
async function refresh() {
  loading.value = true;
  try {
    items.value = await patientsApi.list();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "加载失败");
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingId.value = null;
  form.idCard = "";
  form.gender = "";
  form.birthDate = "";
  form.address = "";
  form.emergencyContact = "";
  form.emergencyPhone = "";
  form.medicalHistory = "";
  dialogOpen.value = true;
}

function openEdit(row: Patient) {
  editingId.value = row.id;
  form.idCard = row.idCard;
  form.gender = row.gender ?? "";
  form.birthDate = row.birthDate ?? "";
  form.address = row.address ?? "";
  form.emergencyContact = row.emergencyContact ?? "";
  form.emergencyPhone = row.emergencyPhone ?? "";
  form.medicalHistory = row.medicalHistory ?? "";
  dialogOpen.value = true;
}

async function submit() {
  if (!form.idCard?.trim()) {
    ElMessage.warning("请输入身份证号");
    return;
  }

  submitting.value = true;
  try {
    if (!editingId.value) {
      const payload: CreatePatientPayload = {
        userId: null,
        idCard: form.idCard.trim(),
        gender: form.gender?.trim() || undefined,
        birthDate: form.birthDate || undefined,
        address: form.address?.trim() || undefined,
        emergencyContact: form.emergencyContact?.trim() || undefined,
        emergencyPhone: form.emergencyPhone?.trim() || undefined,
        medicalHistory: form.medicalHistory?.trim() || undefined
      };
      await patientsApi.create(payload);
      ElMessage.success("新增成功");
    } else {
      const payload: UpdatePatientPayload = {
        gender: form.gender?.trim() || undefined,
        birthDate: form.birthDate || undefined,
        address: form.address?.trim() || undefined,
        emergencyContact: form.emergencyContact?.trim() || undefined,
        emergencyPhone: form.emergencyPhone?.trim() || undefined,
        medicalHistory: form.medicalHistory?.trim() || undefined
      };
      await patientsApi.update(editingId.value, payload);
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

/**
 * 删除患者：二次确认后调用删除接口（仅管理员可见）。
 * 备注：若该患者有关联预约或病历，后端会因外键约束返回错误。
 */
async function handleDelete(row: Patient) {
  try {
    await ElMessageBox.confirm(`确认删除患者记录吗？`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning"
    });
    await patientsApi.delete(row.id);
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

onMounted(refresh);
</script>

<template>
  <section>
    <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
      <h2 style="margin: 0">患者管理</h2>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增患者</el-button>
    </div>

    <el-table v-loading="loading" :data="items" style="width: 100%; margin-top: 14px" border>
      <el-table-column type="index" label="序号" width="60" />
      <el-table-column prop="idCard" label="身份证号" width="200" />
      <el-table-column prop="gender" label="性别" width="100" />
      <el-table-column prop="birthDate" label="出生日期" width="140" />
      <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
      <el-table-column prop="emergencyContact" label="紧急联系人" width="120" />
      <el-table-column prop="emergencyPhone" label="紧急电话" width="130" />
      <el-table-column prop="medicalHistory" label="病史" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button size="small" :icon="Edit" @click="openEdit(row)">编辑</el-button>
          <!-- 删除按钮：仅管理员可见 -->
          <el-button v-if="userRole === 'ADMIN'" size="small" type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && items.length === 0" description="暂无患者数据" />

    <el-dialog v-model="dialogOpen" :title="editingId ? '编辑患者' : '新增患者'" width="640px">
      <el-form label-width="100px">
        <el-form-item label="身份证号" required>
          <el-input v-model="form.idCard" placeholder="例如：110101199001011234" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio label="男">男</el-radio>
            <el-radio label="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="出生日期">
          <el-date-picker v-model="form.birthDate" type="date" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" placeholder="例如：北京市朝阳区" />
        </el-form-item>
        <el-form-item label="紧急联系人">
          <el-input v-model="form.emergencyContact" placeholder="例如：张三" />
        </el-form-item>
        <el-form-item label="紧急电话">
          <el-input v-model="form.emergencyPhone" placeholder="例如：13800138000" />
        </el-form-item>
        <el-form-item label="既往病史">
          <el-input v-model="form.medicalHistory" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>

