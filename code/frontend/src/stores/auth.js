// Lưu phiên đăng nhập TRONG BỘ NHỚ (không dùng localStorage/cookie theo đúng nguyên tắc bảo mật của nhóm)
import { computed, ref } from 'vue';
import { defineStore } from 'pinia';

const roleLabels = { ADMIN: 'Quản trị viên', MANAGER: 'Quản lý', STAFF: 'Nhân viên' };

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null);
  const accessToken = ref(null);
  const authMessage = ref('');
  let expiryTimer = null;

  const isAuthenticated = computed(() => Boolean(user.value && accessToken.value));
  const role = computed(() => user.value?.roles?.[0] || null);
  const isAdmin = computed(() => role.value === 'ADMIN');
  const isManagerOrAdmin = computed(() => role.value === 'ADMIN' || role.value === 'MANAGER');
  const roleLabel = computed(() => roleLabels[role.value] || 'Nhân viên');
  const accessSummary = computed(() =>
    role.value === 'ADMIN' ? 'Bán hàng · Quản lý · Quản trị'
      : role.value === 'MANAGER' ? 'Bán hàng · Quản lý' : 'Bán hàng');

  function clearSession(message = '') {
    accessToken.value = null;
    user.value = null;
    clearTimeout(expiryTimer);
    authMessage.value = message;
  }

  function setSession(data) {
    accessToken.value = data.accessToken;
    user.value = data.user;
    authMessage.value = '';
    clearTimeout(expiryTimer);
    expiryTimer = setTimeout(() => {
      clearSession('Phiên đã hết hạn. Vui lòng đăng nhập lại.');
    }, (data.expiresIn || 900) * 1000);
  }

  return {
    user,
    accessToken,
    authMessage,
    isAuthenticated,
    role,
    isAdmin,
    isManagerOrAdmin,
    roleLabel,
    accessSummary,
    clearSession,
    setSession,
  };
});

