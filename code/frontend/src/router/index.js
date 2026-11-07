import { createMemoryHistory, createRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import PosView from '../views/PosView.vue';
import InvoicesView from '../views/InvoicesView.vue';
import CatalogView from '../views/CatalogView.vue';
import StockView from '../views/StockView.vue';
import ReportsView from '../views/ReportsView.vue';
import AccountsView from '../views/AccountsView.vue';

export const router = createRouter({
  // Dùng memory history để mọi URL đều hoạt động trong SPA được gói tĩnh trong Spring Boot
  history: createMemoryHistory(),
  routes: [
    { path: '/', redirect: '/pos' },
    { path: '/pos', name: 'pos', component: PosView, meta: { icon: '🛒', label: 'Quầy thu ngân (POS)', roles: ['ADMIN', 'MANAGER', 'STAFF'] } },
    { path: '/invoices', name: 'invoices', component: InvoicesView, meta: { icon: '🧾', label: 'Hoá đơn', roles: ['ADMIN', 'MANAGER', 'STAFF'] } },
    { path: '/catalog', name: 'catalog', component: CatalogView, meta: { icon: '📦', label: 'Hàng hoá & Giá bán', roles: ['ADMIN', 'MANAGER'] } },
    { path: '/stock', name: 'stock', component: StockView, meta: { icon: '🏭', label: 'Kho & Nhập hàng', roles: ['ADMIN', 'MANAGER'] } },
    { path: '/reports', name: 'reports', component: ReportsView, meta: { icon: '🤖', label: 'Báo cáo & Trợ lý AI', roles: ['ADMIN', 'MANAGER'] } },
    { path: '/accounts', name: 'accounts', component: AccountsView, meta: { icon: '🔐', label: 'Tài khoản & Mật khẩu', roles: ['ADMIN', 'MANAGER', 'STAFF'] } },
  ],
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  if (!auth.isAuthenticated) return true;
  const allowed = to.meta?.roles;
  if (allowed && !allowed.includes(auth.role)) {
    return '/pos';
  }
  return true;
});
