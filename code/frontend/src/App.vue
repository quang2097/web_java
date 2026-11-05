<script setup>
import { computed, onUnmounted, ref } from 'vue';
const accountName = ref('');
const password = ref('');
const visible = ref(false);
const busy = ref(false);
const message = ref('');
const user = ref(null);
let accessToken = null; // Deliberately in memory: no localStorage or cookies.
let expiryTimer;
const roleLabels = { ADMIN: 'Quản trị viên', MANAGER: 'Quản lý', STAFF: 'Nhân viên' };
const roleLabel = computed(() => roleLabels[user.value?.roles[0]] || 'Nhân viên');
const access = computed(() => {
  const role = user.value?.roles[0];
  return role === 'ADMIN' ? 'Bán hàng · Quản lý · Quản trị' : role === 'MANAGER' ? 'Bán hàng · Quản lý' : 'Bán hàng';
});
function clearAuthentication(text = '') {
  accessToken = null;
  user.value = null;
  password.value = '';
  visible.value = false;
  clearTimeout(expiryTimer);
  message.value = text;
}
async function login() {
  if (busy.value) return;
  busy.value = true;
  message.value = '';
  try {
    const response = await fetch('/api/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ accountName: accountName.value.trim(), password: password.value }),
    });
    if (!response.ok) {
      message.value = response.status === 401
        ? 'Tên tài khoản hoặc mật khẩu không đúng, hoặc tài khoản đã bị khóa.'
        : response.status === 400 ? 'Vui lòng kiểm tra tên tài khoản và mật khẩu.'
        : 'Không thể đăng nhập. Vui lòng thử lại sau.';
      return;
    }
    const data = await response.json();
    accessToken = data.accessToken;
    user.value = data.user;
    password.value = '';
    visible.value = false;
    clearTimeout(expiryTimer);
    expiryTimer = setTimeout(() => clearAuthentication('Phiên đã hết hạn. Vui lòng đăng nhập lại.'), data.expiresIn * 1000);
  } catch {
    message.value = 'Không thể kết nối máy chủ. Vui lòng thử lại.';
  } finally { busy.value = false; }
}
async function logout() {
  if (busy.value) return;
  busy.value = true;
  message.value = '';
  try {
    const response = await fetch('/api/auth/logout', { method: 'POST', headers: { Authorization: `Bearer ${accessToken}` } });
    if (!response.ok && response.status !== 401) throw new Error('logout');
    clearAuthentication('Bạn đã đăng xuất.');
  } catch {
    message.value = 'Không thể đăng xuất trên máy chủ. Vui lòng thử lại.';
  } finally { busy.value = false; }
}
onUnmounted(() => clearTimeout(expiryTimer));
</script>

<template>
  <main class="shell">
    <section class="intro" aria-label="Supermarket">
      <a class="brand" href="/" aria-label="Supermarket trang chủ"><span class="brand-icon">S</span> supermarket<span class="dot">.</span></a>
      <div class="intro-copy"><span class="eyebrow">HỆ THỐNG QUẢN LÝ SIÊU THỊ</span><h1>Mỗi ngày vận hành.<br>Mọi việc dễ dàng.</h1><p>Một nơi để bắt đầu ngày làm việc của bạn.<br>Đăng nhập để truy cập hệ thống quản lý.</p></div>
      <div class="store" aria-hidden="true"><div class="store-roof"></div><div class="awning"><i v-for="n in 7" :key="n"></i></div><div class="store-body"><div class="window"><span>✳</span></div><div class="door"></div><div class="window"><span>✳</span></div></div><div class="ground"></div></div>
      <footer>Đồng hành cùng cửa hàng của bạn.</footer>
    </section>
    <section class="panel">
      <div v-if="!user" class="login-wrap">
        <span class="section-label">CHÀO MỪNG TRỞ LẠI</span><h2>Đăng nhập</h2><p class="muted">Nhập thông tin tài khoản để tiếp tục.</p>
        <form @submit.prevent="login">
          <label for="accountName">Tên tài khoản</label>
          <input id="accountName" v-model="accountName" name="accountName" autocomplete="username" placeholder="Nhập tên tài khoản" minlength="3" maxlength="64" pattern="[A-Za-z0-9_.\-]{3,64}" title="3–64 ký tự: chữ không dấu, số, dấu chấm, gạch dưới hoặc gạch ngang" required autofocus :disabled="busy">
          <label for="password">Mật khẩu</label>
          <div class="password-field"><input id="password" v-model="password" name="password" :type="visible ? 'text' : 'password'" autocomplete="current-password" placeholder="Nhập mật khẩu" maxlength="72" required :disabled="busy"><button type="button" :aria-label="visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'" :aria-pressed="visible" @click="visible = !visible">{{ visible ? 'Ẩn' : 'Hiện' }}</button></div>
          <div class="forgot-row"><button type="button" class="text-button" @click="message = 'Vui lòng liên hệ quản trị viên siêu thị để được cấp lại mật khẩu.'">Quên mật khẩu?</button></div>
          <p id="message" role="status" aria-live="polite">{{ message }}</p>
          <button class="primary" type="submit" :disabled="busy">{{ busy ? 'Đang đăng nhập…' : 'Đăng nhập' }} <span aria-hidden="true">→</span></button>
        </form>
        <p class="help">Chưa có tài khoản? <span>Liên hệ quản trị viên.</span></p>
        <div class="secure"><span aria-hidden="true">▣</span> Truy cập dành cho nhân viên được cấp quyền</div>
      </div>
      <div v-else class="login-wrap">
        <span class="section-label">ĐĂNG NHẬP THÀNH CÔNG</span><h2>Xin chào!</h2>
        <p>Bạn đã đăng nhập với tài khoản <strong>{{ user.accountName }}</strong>.</p>
        <div class="identity-card"><strong>{{ user.fullName }}</strong><span>{{ roleLabel }}</span><p>Quyền truy cập: {{ access }}</p></div>
        <p class="muted">Phiên làm việc có hiệu lực trong 15 phút.</p>
        <button class="primary" :disabled="busy" @click="logout">{{ busy ? 'Đang đăng xuất…' : 'Đăng xuất' }} <span aria-hidden="true">→</span></button>
        <p role="status" aria-live="polite">{{ message }}</p>
      </div>
      <div class="panel-footer">SUPERMARKET MANAGEMENT <span>© 2026</span></div>
    </section>
  </main>
</template>
