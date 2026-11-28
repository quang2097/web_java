<script setup>
import { computed, reactive, ref } from 'vue';
import { apiFetch, formatVnd } from '../api/http';

const categories = ref([]);
const products = ref([]);
const newCategoryName = ref('');
const notice = ref('');
const saving = ref(false);
const loading = ref(false);
const errorText = ref('');

const form = reactive({
  name: '',
  categoryId: null,
  description: '',
  code: '',
  variant: '',
  unit: 'gói',
  unitPrice: 10000,
});

const editSkuModal = ref(null);
const editProduct = ref(null);
const categoryNames = reactive({});
const query = ref('');
const filteredProducts = computed(() => products.value.filter(p =>
  `${p.name} ${p.categoryName || ''} ${p.skus.map(s => s.code).join(' ')}`.toLocaleLowerCase('vi').includes(query.value.trim().toLocaleLowerCase('vi'))));

async function mutate(path, method, body, message) {
  if (saving.value) return false;
  saving.value = true;
  errorText.value = '';
  notice.value = '';
  try {
    await apiFetch(path, { method, ...(body === undefined ? {} : { body }) });
    notice.value = message;
    await loadAll();
    return true;
  } catch (err) { errorText.value = err.message; return false; }
  finally { saving.value = false; }
}
async function saveProduct() {
  if (await mutate(`/api/management/products/${editProduct.value.id}`, 'PUT', editProduct.value, 'Đã cập nhật sản phẩm.')) editProduct.value = null;
}
function startAddSku(product) {
  editSkuModal.value = { productId: product.id, code: '', variant: '', unit: 'cái', unitPrice: 0 };
}

async function loadAll() {
  if (loading.value) return;
  loading.value = true;
  errorText.value = '';
  try {
    const [cats, prods] = await Promise.all([
      apiFetch('/api/pos/categories'),
      apiFetch('/api/management/products'),
    ]);
    categories.value = cats;
    cats.forEach(c => { categoryNames[c.id] = c.name; });
    products.value = prods;
    if (cats.length && !form.categoryId) form.categoryId = cats[0].id;
  } catch (err) {
    errorText.value = err.message;
  } finally {
    loading.value = false;
  }
}

async function addCategory() {
  if (!newCategoryName.value.trim()) return;
  if (saving.value) return;
  saving.value = true;
  notice.value = '';
  errorText.value = '';
  try {
    await apiFetch('/api/management/categories', {
      method: 'POST',
      body: { name: newCategoryName.value.trim() },
    });
    newCategoryName.value = '';
    notice.value = 'Đã thêm danh mục mới.';
    await loadAll();
  } catch (err) {
    errorText.value = err.message;
  } finally {
    saving.value = false;
  }
}

async function createProduct() {
  if (saving.value) return;
  saving.value = true;
  notice.value = '';
  errorText.value = '';
  try {
    await apiFetch('/api/management/products', {
      method: 'POST',
      body: {
        name: form.name,
        description: form.description,
        categoryId: form.categoryId,
        active: true,
        skus: [
          {
            code: form.code,
            variant: form.variant,
            unit: form.unit,
            unitPrice: Number(form.unitPrice),
          },
        ],
      },
    });
    form.name = '';
    form.description = '';
    form.code = '';
    form.variant = '';
    notice.value = 'Đã tạo mặt hàng mới và khởi tạo tồn kho = 0.';
    await loadAll();
  } catch (err) {
    errorText.value = err.message;
  } finally {
    saving.value = false;
  }
}

function startEditSku(sku) {
  editSkuModal.value = {
    id: sku.id,
    code: sku.code,
    variant: sku.variant,
    unit: sku.unit,
    unitPrice: Number(sku.unitPrice),
  };
}

async function saveSku() {
  if (!editSkuModal.value) return;
  if (saving.value) return;
  saving.value = true;
  notice.value = '';
  errorText.value = '';
  try {
    const sku = editSkuModal.value;
    await apiFetch(sku.id ? `/api/management/skus/${sku.id}` : `/api/management/products/${sku.productId}/skus`, {
      method: sku.id ? 'PUT' : 'POST',
      body: { ...sku, unitPrice: Number(sku.unitPrice) },
    });
    editSkuModal.value = null;
    notice.value = 'Đã cập nhật giá và thông tin SKU.';
    await loadAll();
  } catch (err) {
    errorText.value = err.message;
  } finally {
    saving.value = false;
  }
}

loadAll();
</script>

<template>
  <fieldset class="view-fields" :disabled="saving" :aria-busy="saving || loading">
  <p v-if="loading" class="loading-hint" role="status">Đang tải dữ liệu…</p>
  <div class="pos-grid">
    <section class="card">
      <div class="card-head">
        <div>
          <span class="badge">DANH MỤC & MẶT HÀNG</span>
          <h3>Thêm sản phẩm / mã vạch mới</h3>
        </div>
      </div>

      <p v-if="notice" class="alert ok" role="status">{{ notice }}</p>
      <p v-if="errorText" class="alert err" role="alert">{{ errorText }}</p>

      <form class="bar-row compact" @submit.prevent="addCategory">
        <input v-model="newCategoryName" maxlength="120" aria-label="Tên danh mục mới" required placeholder="Tên danh mục mới (VD: Đồ đông lạnh)..." />
        <button type="submit" class="btn-outline">+ Danh mục</button>
      </form>

      <details>
        <summary>Chỉnh sửa danh mục</summary>
        <form v-for="c in categories" :key="c.id" class="bar-row compact" @submit.prevent="mutate(`/api/management/categories/${c.id}`, 'PUT', { name: categoryNames[c.id] }, 'Đã đổi tên danh mục.')">
          <input v-model="categoryNames[c.id]" required maxlength="120" :aria-label="`Tên danh mục ${c.name}`" />
          <button type="submit" class="btn-sm">Lưu</button>
          <button type="button" class="btn-ghost" :disabled="products.some(p => p.categoryId === c.id)" @click="mutate(`/api/management/categories/${c.id}`, 'DELETE', undefined, 'Đã xoá danh mục.')">Xoá danh mục trống</button>
        </form>
      </details>

      <form class="stack-form" @submit.prevent="createProduct">
        <label for="catalogview-field-1">Tên sản phẩm</label>
        <input id="catalogview-field-1" v-model="form.name" maxlength="160" required placeholder="VD: Sữa chua uống Yomost" />

        <div class="two-col">
          <div>
            <label for="catalogview-field-2">Danh mục</label>
            <select id="catalogview-field-2" v-model="form.categoryId">
              <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </div>
          <div>
            <label for="catalogview-field-3">Mã vạch / SKU</label>
            <input id="catalogview-field-3" v-model="form.code" pattern="[A-Za-z0-9_\-]{3,64}" maxlength="64" required placeholder="VD: 8934841001234" />
          </div>
        </div>

        <div class="three-col">
          <div>
            <label for="catalogview-field-4">Quy cách</label>
            <input id="catalogview-field-4" v-model="form.variant" maxlength="120" placeholder="VD: Lốc 4 hộp" />
          </div>
          <div>
            <label for="catalogview-field-5">Đơn vị tính</label>
            <input id="catalogview-field-5" v-model="form.unit" maxlength="32" required placeholder="hộp / chai / gói" />
          </div>
          <div>
            <label for="catalogview-field-6">Giá bán (VNĐ)</label>
            <input id="catalogview-field-6" v-model="form.unitPrice" type="number" min="0" step="1" max="999999999999" required />
          </div>
        </div>

        <button type="submit" class="btn-solid full">+ Lưu mặt hàng</button>
      </form>

      <form v-if="editProduct" class="stack-form ai-box" @submit.prevent="saveProduct">
        <h4>Sửa sản phẩm #{{ editProduct.id }}</h4>
        <label for="edit-product-name">Tên sản phẩm</label>
        <input id="edit-product-name" v-model="editProduct.name" required maxlength="160" />
        <label for="edit-product-category">Danh mục</label>
        <select id="edit-product-category" v-model="editProduct.categoryId">
          <option :value="null">Không có danh mục</option>
          <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <label for="edit-product-description">Mô tả</label>
        <input id="edit-product-description" v-model="editProduct.description" maxlength="1000" />
        <label class="check-label"><input v-model="editProduct.active" type="checkbox" /> Đang kinh doanh</label>
        <div class="bar-row"><button type="submit" class="btn-solid">Lưu sản phẩm</button><button type="button" class="btn-outline" @click="editProduct = null">Đóng</button></div>
      </form>

      <!-- Sửa nhanh SKU -->
      <form @submit.prevent="saveSku" v-if="editSkuModal" class="ai-box" style="margin-top: 18px">
        <strong>{{ editSkuModal.id ? `Cập nhật SKU #${editSkuModal.id}` : 'Thêm SKU mới' }}</strong>
        <div class="two-col">
          <div>
            <label for="catalogview-field-7">Mã vạch</label>
            <input id="catalogview-field-7" v-model="editSkuModal.code" required maxlength="64" pattern="[A-Za-z0-9_\-]{3,64}" />
          </div>
          <div>
            <label for="catalogview-field-8">Quy cách</label>
            <input id="catalogview-field-8" v-model="editSkuModal.variant" maxlength="120" />
          </div>
        </div>
        <div class="two-col">
          <div>
            <label for="catalogview-field-9">Đơn vị</label>
            <input id="catalogview-field-9" v-model="editSkuModal.unit" required maxlength="32" />
          </div>
          <div>
            <label for="catalogview-field-10">Giá bán (VNĐ)</label>
            <input id="catalogview-field-10" v-model="editSkuModal.unitPrice" required type="number" min="0" step="1" max="999999999999" />
          </div>
        </div>
        <div class="bar-row compact">
          <button type="submit" class="btn-solid">Lưu thay đổi</button>
          <button type="button" class="btn-outline" @click="editSkuModal = null">Đóng</button>
        </div>
      </form>
    </section>

    <section class="card">
      <div class="card-head">
        <div>
          <span class="badge">DANH SÁCH HÀNG HOÁ ({{ products.length }})</span>
          <h3>Bảng giá & tồn kho hiện tại</h3>
        </div>
        <button type="button" class="btn-outline" :disabled="loading" @click="loadAll">Làm mới</button>
      </div>

      <input v-model="query" type="search" aria-label="Tìm trong danh sách hàng hoá" placeholder="Tìm tên sản phẩm, danh mục hoặc mã SKU…" />

      <table class="data-table">
        <thead>
          <tr>
            <th>Mã vạch</th>
            <th>Sản phẩm</th>
            <th>Danh mục</th>
            <th>Giá bán</th>
            <th>Tồn</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <template v-for="p in filteredProducts" :key="p.id">
            <tr v-for="s in p.skus" :key="s.id">
              <td><code>{{ s.code }}</code></td>
              <td>
                <strong>{{ p.name }}</strong>
                <span v-if="!p.active" class="tag warn">Ngừng kinh doanh</span>
                <div class="meta">{{ s.variant }} · {{ s.unit }}</div>
              </td>
              <td>{{ p.categoryName || '—' }}</td>
              <td><strong>{{ formatVnd(s.unitPrice) }}</strong></td>
              <td>{{ s.stock }}</td>
              <td>
                <button type="button" class="btn-sm" @click="startEditSku(s)">Sửa giá</button>
                <button type="button" class="btn-ghost" @click="editProduct = { ...p, skus: undefined }">Sửa sản phẩm</button>
                <button type="button" class="btn-ghost" @click="startAddSku(p)">+ SKU</button>
              </td>
            </tr>
          </template>
          <tr v-if="!loading && !filteredProducts.length"><td colspan="6" class="empty-hint">Chưa có sản phẩm phù hợp.</td></tr>
        </tbody>
      </table>
    </section>
  </div>
  </fieldset>
</template>

