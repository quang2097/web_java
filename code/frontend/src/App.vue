<script setup>
import { computed, reactive, ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { apiFetch } from './api/http';
import { useAuthStore } from './stores/auth';

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();

const accountName = ref('');
const password = ref('');
const visible = ref(false);
const busy = ref(false);
const showForgot = ref(false);

const forgotForm = reactive({
  accountName: '',
  fullName: '',
  employeeCode: '',
  reason: '',
});

const navItems = computed(() =>
  router.getRoutes()
    .filter((r) => r.meta?.label && r.meta.roles.includes(auth.role))
);

async function login() {
  if (busy.value) return;
  busy.value = true;
  auth.authMessage = '';
  try {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        accountName: accountName.value.trim(),
        password: password.value,
      }),
    });
    if (!response.ok) {
      auth.authMessage = response.status === 401
        ? 'Tên tài khoản hoặc mật khẩu không đúng, hoặc tài khoản đã bị khóa.'
        : response.status === 400
          ? 'Vui lòng kiểm tra tên tài khoản và mật khẩu.'
          : 'Không thể đăng nhập. Vui lòng thử lại sau.';
      return;
    }
    const data = await response.json();
    auth.setSession(data);
    password.value = '';
    visible.value = false;
    router.push('/pos');
  } catch {
    auth.authMessage = 'Không thể kết nối máy chủ. Vui lòng thử lại.';
  } finally {
    busy.value = false;
  }
}

async function submitForgotRequest() {
  if (busy.value) return;
  busy.value = true;
  auth.authMessage = '';
  try {
    const res = await apiFetch('/api/auth/password-requests', {
      method: 'POST',
      body: {
        accountName: forgotForm.accountName.trim(),
        fullName: forgotForm.fullName.trim(),
        employeeCode: forgotForm.employeeCode.trim(),
        reason: forgotForm.reason.trim(),
      },
    });
    auth.authMessage = res?.message || 'Yêu cầu đổi mật khẩu đã được gửi.';
    showForgot.value = false;
    forgotForm.accountName = '';
    forgotForm.fullName = '';
    forgotForm.employeeCode = '';
    forgotForm.reason = '';
  } catch (err) {
    auth.authMessage = err.message;
  } finally {
    busy.value = false;
  }
}

async function logout() {
  if (busy.value) return;
  busy.value = true;
  try {
    await fetch('/api/auth/logout', {
      method: 'POST',
      headers: { Authorization: `Bearer ${auth.accessToken}` },
    });
    auth.clearSession('Bạn đã đăng xuất.');
  } catch {
    auth.authMessage = 'Không thể đăng xuất trên máy chủ. Vui lòng thử lại.';
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <!-- Màn hình đăng nhập & gửi yêu cầu quên mật khẩu -->
  <main v-if="!auth.isAuthenticated" class="shell">
    <section class="intro" aria-label="Supermarket">
      <a class="brand" href="/" aria-label="Supermarket trang chủ">
        <span class="brand-icon">S</span> supermarket<span class="dot">.</span>
      </a>
      <div class="intro-copy">
        <span class="eyebrow">HỆ THỐNG QUẢN LÝ SIÊU THỊ TÍCH HỢP AI</span>
        <h1>Mỗi ngày vận hành.<br />Mọi việc dễ dàng.</h1>
        <p>
          Bán hàng tại quầy, quản lý tồn kho, thanh toán VietQR<br />
          và trợ lý AI phân tích dữ liệu.
        </p>
      </div>
      <div class="store" aria-hidden="true">
        <div class="store-roof"></div>
        <div class="awning"><i v-for="n in 7" :key="n"></i></div>
        <div class="store-body">
          <div class="window"><span>✳</span></div>
          <div class="door"></div>
          <div class="window"><span>✳</span></div>
        </div>
        <div class="ground"></div>
      </div>
      <footer>Đồng hành cùng cửa hàng của bạn.</footer>
    </section>

    <section class="panel">
      <!-- Form Đăng nhập -->
      <div v-if="!showForgot" class="login-wrap">
        <span class="section-label">CHÀO MỪNG TRỞ LẠI</span>
        <h2>Đăng nhập</h2>
        <p class="muted">Nhập thông tin tài khoản để tiếp tục.</p>
        <form @submit.prevent="login">
          <label for="accountName">Tên tài khoản</label>
          <input
            id="accountName"
            v-model="accountName"
            name="accountName"
            autocomplete="username"
            placeholder="Nhập tên tài khoản"
            minlength="3"
            maxlength="64"
            pattern="[A-Za-z0-9_.\-]{3,64}"
            title="3–64 ký tự: chữ không dấu, số, dấu chấm, gạch dưới hoặc gạch ngang"
            required
            autofocus
            :disabled="busy"
          />
          <label for="password">Mật khẩu</label>
          <div class="password-field">
            <input
              id="password"
              v-model="password"
              name="password"
              :type="visible ? 'text' : 'password'"
              autocomplete="current-password"
              placeholder="Nhập mật khẩu"
              maxlength="72"
              required
              :disabled="busy"
            />
            <button
              type="button"
              :aria-label="visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'"
              :aria-pressed="visible"
              @click="visible = !visible"
            >
              {{ visible ? 'Ẩn' : 'Hiện' }}
            </button>
          </div>
          <div class="forgot-row">
            <button type="button" class="text-button" @click="showForgot = true">
              Quên mật khẩu? Tạo yêu cầu cấp lại
            </button>
          </div>
          <p id="message" role="status" aria-live="polite">{{ auth.authMessage }}</p>
          <button class="primary" type="submit" :disabled="busy">
            {{ busy ? 'Đang đăng nhập…' : 'Đăng nhập' }} <span aria-hidden="true">→</span>
          </button>
        </form>
        <p class="help">Chưa có tài khoản? <span>Liên hệ Quản lý hoặc Quản trị viên.</span></p>
        <div class="secure"><span aria-hidden="true">▣</span> Truy cập dành cho nhân viên được cấp quyền</div>
      </div>

      <!-- Form Ca sử dụng: Quên mật khẩu -> Tạo yêu cầu đổi mật khẩu (nhập thông tin cá nhân) -->
      <div v-else class="login-wrap">
        <span class="section-label">CA SỬ DỤNG · QUÊN MẬT KHẨU</span>
        <h2>Yêu cầu cấp lại mật khẩu</h2>
        <p class="muted">
          Nhập thông tin cá nhân đã đăng ký để gửi yêu cầu tới Quản lý / Quản trị viên.
        </p>
        <form @submit.prevent="submitForgotRequest">
          <label>Tên tài khoản</label>
          <input
            v-model="forgotForm.accountName"
            required
            pattern="[A-Za-z0-9_.\-]{3,64}"
            placeholder="VD: cashier"
            :disabled="busy"
          />
          <label>Họ và tên nhân viên</label>
          <input
            v-model="forgotForm.fullName"
            required
            placeholder="Nhập đúng họ tên trên hồ sơ"
            :disabled="busy"
          />
          <label>Mã nhân viên</label>
          <input
            v-model="forgotForm.employeeCode"
            required
            placeholder="VD: E001"
            :disabled="busy"
          />
          <label>Lý do / Ghi chú (tuỳ chọn)</label>
          <input
            v-model="forgotForm.reason"
            placeholder="VD: Quên mật khẩu sau kỳ nghỉ"
            :disabled="busy"
          />
          <p id="message" role="status" aria-live="polite">{{ auth.authMessage }}</p>
          <button class="primary" type="submit" :disabled="busy">
            {{ busy ? 'Đang gửi yêu cầu…' : 'Gửi yêu cầu đổi mật khẩu' }} <span aria-hidden="true">→</span>
          </button>
        </form>
        <div class="forgot-row" style="text-align: center; margin-top: 16px">
          <button type="button" class="text-button" @click="showForgot = false">
            ← Quay lại màn hình đăng nhập
          </button>
        </div>
      </div>

      <div class="panel-footer">SUPERMARKET MANAGEMENT <span>© 2026</span></div>
    </section>
  </main>

  <!-- Bố cục ứng dụng sau khi đăng nhập -->
  <div v-else class="app-layout">
    <header class="topbar">
      <div class="topbar-brand">
        <span class="brand-icon">S</span>
        <div>
          <strong>supermarket.</strong>
          <small>{{ auth.accessSummary }}</small>
        </div>
      </div>

      <nav class="topnav">
        <button
          v-for="item in navItems"
          :key="item.path"
          type="button"
          :class="['nav-btn', { active: route.path === item.path }]"
          @click="router.push(item.path)"
        >
          <span class="nav-ico">{{ item.meta.icon }}</span>
          <span>{{ item.meta.label }}</span>
        </button>
      </nav>

      <div class="topbar-user">
        <div class="live-pill" title="Máy chủ đang kết nối">
          <span class="live-dot"></span> Trực tuyến
        </div>
        <div class="user-pill">
          <strong>{{ auth.user?.fullName }}</strong>
          <span>{{ auth.roleLabel }} ({{ auth.user?.accountName }})</span>
        </div>
        <button type="button" class="btn-logout" :disabled="busy" @click="logout">
          Đăng xuất
        </button>
      </div>
    </header>

    <main class="workspace">
      <RouterView v-slot="{ Component }">
        <Transition name="page-fade" mode="out-in">
          <component :is="Component" :key="route.path" />
        </Transition>
      </RouterView>
    </main>
  </div>
</template>
