<script setup>
import { reactive, ref } from 'vue';
import { apiFetch, formatDateTime } from '../api/http';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();
const notice = ref('');
const errorText = ref('');

// Đổi mật khẩu của chính mình
const pwdForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
});

// Quản lý tài khoản & xử lý yêu cầu đổi mật khẩu (MANAGER / ADMIN)
const accounts = ref([]);
const requests = ref([]);
const newPassByReq = reactive({});

const accForm = reactive({
  accountName: '',
  password: '',
  fullName: '',
  employeeCode: '',
  role: 'STAFF',
});

async function loadManagementData() {
  if (!auth.isManagerOrAdmin) return;
  try {
    const [accs, reqs] = await Promise.all([
      apiFetch('/api/management/accounts'),
      apiFetch('/api/management/password-requests'),
    ]);
    accounts.value = accs;
    requests.value = reqs;
  } catch (err) {
    errorText.value = err.message;
  }
}

async function submitChangePassword() {
  notice.value = '';
  errorText.value = '';
  try {
    await apiFetch('/api/auth/change-password', {
      method: 'POST',
      body: pwdForm,
    });
    pwdForm.currentPassword = '';
    pwdForm.newPassword = '';
    pwdForm.confirmPassword = '';
    // Khi đổi mật khẩu, @Version tăng nên token cũ bị thu hồi tự động -> yêu cầu đăng nhập lại bằng mật khẩu mới
    auth.clearSession('Đổi mật khẩu thành công. Vui lòng đăng nhập lại bằng mật khẩu mới.');
  } catch (err) {
    errorText.value = err.message;
  }
}

async function createAccount() {
  notice.value = '';
  errorText.value = '';
  try {
    await apiFetch('/api/management/accounts', {
      method: 'POST',
      body: accForm,
    });
    accForm.accountName = '';
    accForm.password = '';
    accForm.fullName = '';
    accForm.employeeCode = '';
    notice.value = 'Đã tạo tài khoản mới thành công.';
    await loadManagementData();
  } catch (err) {
    errorText.value = err.message;
  }
}

async function toggleEnabled(acc) {
  notice.value = '';
  errorText.value = '';
  try {
    await apiFetch(`/api/management/accounts/${acc.id}/enabled`, {
      method: 'PUT',
      body: { enabled: !acc.enabled },
    });
    await loadManagementData();
  } catch (err) {
    errorText.value = err.message;
  }
}

async function approveRequest(req) {
  const newPassword = (newPassByReq[req.id] || '').trim();
  if (!newPassword) {
    errorText.value = 'Nhập mật khẩu mới (8–72 ký tự) trước khi duyệt.';
    return;
  }
  notice.value = '';
  errorText.value = '';
  try {
    await apiFetch(`/api/management/password-requests/${req.id}/approve`, {
      method: 'POST',
      body: { newPassword },
    });
    newPassByReq[req.id] = '';
    notice.value = `Đã cấp lại mật khẩu cho tài khoản ${req.accountName}.`;
    await loadManagementData();
  } catch (err) {
    errorText.value = err.message;
  }
}

async function rejectRequest(req) {
  notice.value = '';
  errorText.value = '';
  try {
    await apiFetch(`/api/management/password-requests/${req.id}/reject`, { method: 'POST' });
    notice.value = `Đã từ chối yêu cầu của ${req.accountName}.`;
    await loadManagementData();
  } catch (err) {
    errorText.value = err.message;
  }
}

loadManagementData();
</script>

<template>
  <div class="pos-grid">
    <!-- Cột trái: Thông tin phiên hiện tại & Đổi mật khẩu -->
    <section class="card">
      <div class="card-head">
        <div>
          <span class="badge">CA SỬ DỤNG · ĐỔI MẬT KHẨU</span>
          <h3>Tài khoản của bạn ({{ auth.user?.accountName }})</h3>
        </div>
      </div>

      <div class="identity-card">
        <strong>{{ auth.user?.fullName }}</strong>
        <span>{{ auth.roleLabel }}</span>
        <p>Quyền truy cập: {{ auth.accessSummary }}</p>
      </div>

      <p v-if="notice" class="alert ok">{{ notice }}</p>
      <p v-if="errorText" class="alert err">{{ errorText }}</p>

      <form class="stack-form" @submit.prevent="submitChangePassword">
        <label>Mật khẩu hiện tại</label>
        <input v-model="pwdForm.currentPassword" type="password" required maxlength="72" />

        <label>Mật khẩu mới (8–72 ký tự ASCII)</label>
        <input v-model="pwdForm.newPassword" type="password" required minlength="8" maxlength="72" />

        <label>Xác nhận mật khẩu mới</label>
        <input v-model="pwdForm.confirmPassword" type="password" required minlength="8" maxlength="72" />

        <button type="submit" class="btn-solid full">Cập nhật mật khẩu</button>
      </form>
    </section>

    <!-- Cột phải: Chỉ hiển thị cho Quản lý & Admin -->
    <section v-if="auth.isManagerOrAdmin" class="card">
      <div class="card-head">
        <div>
          <span class="badge">
            CA SỬ DỤNG · TẠO TÀI KHOẢN & XỬ LÝ YÊU CẦU ĐỔI MẬT KHẨU
          </span>
          <h3>
            {{ auth.isAdmin ? 'Quản trị toàn bộ tài khoản (Admin)' : 'Quản lý tài khoản Nhân viên (Quản lý)' }}
          </h3>
        </div>
        <button type="button" class="btn-outline" @click="loadManagementData">Làm mới</button>
      </div>

      <!-- Tạo tài khoản mới -->
      <form class="stack-form" @submit.prevent="createAccount">
        <div class="two-col">
          <div>
            <label>Tên tài khoản (3–64 ký tự)</label>
            <input v-model="accForm.accountName" required pattern="[A-Za-z0-9_.\-]{3,64}" placeholder="VD: nhanvien01" />
          </div>
          <div>
            <label>Mật khẩu khởi tạo (8–72 ký tự)</label>
            <input v-model="accForm.password" type="password" required minlength="8" maxlength="72" />
          </div>
        </div>

        <div class="three-col">
          <div>
            <label>Họ và tên</label>
            <input v-model="accForm.fullName" required placeholder="Nguyễn Văn A" />
          </div>
          <div>
            <label>Mã nhân viên</label>
            <input v-model="accForm.employeeCode" required placeholder="NV001" />
          </div>
          <div>
            <label>Vai trò</label>
            <select v-model="accForm.role">
              <option value="STAFF">Nhân viên (STAFF)</option>
              <option v-if="auth.isAdmin" value="MANAGER">Quản lý (MANAGER)</option>
              <option v-if="auth.isAdmin" value="ADMIN">Quản trị viên (ADMIN)</option>
            </select>
          </div>
        </div>
        <button type="submit" class="btn-solid full">+ Tạo tài khoản</button>
      </form>

      <!-- Danh sách yêu cầu cấp lại mật khẩu -->
      <h4 style="margin-top: 24px">Yêu cầu cấp lại mật khẩu</h4>
      <table v-if="requests.length" class="data-table">
        <thead>
          <tr>
            <th>Tài khoản</th>
            <th>Nhân viên</th>
            <th>Lý do / Thời gian</th>
            <th>Trạng thái / Xử lý</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in requests" :key="r.id">
            <td>
              <strong>{{ r.accountName }}</strong>
              <div class="meta">{{ r.role }}</div>
            </td>
            <td>{{ r.fullName }} ({{ r.employeeCode }})</td>
            <td>
              {{ r.reason || 'Quên mật khẩu' }}
              <div class="meta">{{ formatDateTime(r.createdAt) }}</div>
            </td>
            <td>
              <div v-if="r.status === 'PENDING'" class="bar-row compact" style="margin: 0">
                <input
                  v-model="newPassByReq[r.id]"
                  type="password"
                  placeholder="Mật khẩu mới..."
                  style="min-height: 34px; padding: 6px 10px"
                />
                <button type="button" class="btn-sm" @click="approveRequest(r)">Duyệt</button>
                <button type="button" class="btn-outline" @click="rejectRequest(r)">Từ chối</button>
              </div>
              <span v-else :class="['tag', r.status === 'APPROVED' ? 'ok' : 'muted-tag']">
                {{ r.status === 'APPROVED' ? 'Đã duyệt' : 'Đã từ chối' }} ({{ r.handledBy }})
              </span>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="empty-hint">Chưa có yêu cầu cấp lại mật khẩu nào.</p>

      <!-- Danh sách tài khoản -->
      <h4 style="margin-top: 24px">Danh sách tài khoản</h4>
      <table class="data-table">
        <thead>
          <tr>
            <th>Tài khoản</th>
            <th>Họ tên / Mã NV</th>
            <th>Vai trò</th>
            <th>Trạng thái</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in accounts" :key="a.id">
            <td><strong>{{ a.accountName }}</strong></td>
            <td>{{ a.fullName }} ({{ a.employeeCode }})</td>
            <td><span class="tag">{{ a.role }}</span></td>
            <td>
              <span :class="['tag', a.enabled ? 'ok' : 'warn']">
                {{ a.enabled ? 'Đang hoạt động' : 'Đã khoá' }}
              </span>
            </td>
            <td>
              <button
                v-if="a.accountName !== auth.user?.accountName"
                type="button"
                class="btn-sm"
                @click="toggleEnabled(a)"
              >
                {{ a.enabled ? 'Khoá' : 'Mở khoá' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

