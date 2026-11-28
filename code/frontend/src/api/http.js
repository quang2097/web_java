import { useAuthStore } from '../stores/auth';

export async function apiFetch(path, options = {}) {
  const auth = useAuthStore();
  const requestToken = auth.accessToken;
  const headers = new Headers(options.headers || {});
  if (auth.accessToken) {
    headers.set('Authorization', `Bearer ${auth.accessToken}`);
  }
  const isFormData = options.body instanceof FormData;
  if (options.body && !isFormData && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  let res;
  try {
    res = await fetch(path, {
      ...options,
      signal: options.signal || AbortSignal.timeout(35000),
      headers,
      body: options.body && !isFormData && typeof options.body !== 'string'
        ? JSON.stringify(options.body)
        : options.body,
    });
  } catch (cause) {
    throw new Error(cause.name === 'TimeoutError'
      ? 'Máy chủ phản hồi quá lâu. Vui lòng thử lại.'
      : 'Không thể kết nối máy chủ. Kiểm tra kết nối và thử lại.', { cause });
  }

  if (res.status === 204) return null;

  let payload = null;
  const contentType = res.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    payload = await res.json().catch(() => null);
  }

  if (!res.ok) {
    if (res.status === 401 && requestToken && requestToken === auth.accessToken && !path.startsWith('/api/auth/login')) {
      auth.clearSession('Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại.');
    }
    const err = new Error(payload?.message || `Yêu cầu thất bại (HTTP ${res.status}).`);
    err.status = res.status;
    throw err;
  }
  if (payload === null) throw new Error('Phản hồi máy chủ không hợp lệ. Vui lòng thử lại.');
  return payload;
}

export function formatVnd(value) {
  const num = Number(value || 0);
  return new Intl.NumberFormat('vi-VN').format(num) + ' đ';
}

export function formatDateTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('vi-VN');
}

