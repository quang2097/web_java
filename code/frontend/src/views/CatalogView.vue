<script setup>
import { reactive, ref } from 'vue';
import { apiFetch, formatVnd } from '../api/http';

const categories = ref([]);
const products = ref([]);
const newCategoryName = ref('');
const notice = ref('');
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

async function loadAll() {
  try {
    const [cats, prods] = await Promise.all([
      apiFetch('/api/pos/categories'),
      apiFetch('/api/management/products'),
    ]);
    categories.value = cats;
    products.value = prods;
    if (cats.length && !form.categoryId) form.categoryId = cats[0].id;
  } catch (err) {
    errorText.value = err.message;
  }
}

async function addCategory() {
  if (!newCategoryName.value.trim()) return;
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
  }
}

async function createProduct() {
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
  notice.value = '';
  errorText.value = '';
  try {
    await apiFetch(`/api/management/skus/${editSkuModal.value.id}`, {
      method: 'PUT',
      body: editSkuModal.value,
    });
    editSkuModal.value = null;
    notice.value = 'Đã cập nhật giá và thông tin SKU.';
    await loadAll();
  } catch (err) {
    errorText.value = err.message;
  }
}

loadAll();
</script>

<template>
  <div class="pos-grid">
    <section class="card">
      <div class="card-head">
        <div>
          <span class="badge">DANH MỤC & MẶT HÀNG</span>
          <h3>Thêm sản phẩm / mã vạch mới</h3>
        </div>
      </div>

      <p v-if="notice" class="alert ok">{{ notice }}</p>
      <p v-if="errorText" class="alert err">{{ errorText }}</p>

      <form class="bar-row compact" @submit.prevent="addCategory">
        <input v-model="newCategoryName" placeholder="Tên danh mục mới (VD: Đồ đông lạnh)..." />
        <button type="submit" class="btn-outline">+ Danh mục</button>
      </form>

      <form class="stack-form" @submit.prevent="createProduct">
        <label>Tên sản phẩm</label>
        <input v-model="form.name" required placeholder="VD: Sữa chua uống Yomost" />

        <div class="two-col">
          <div>
            <label>Danh mục</label>
            <select v-model="form.categoryId">
              <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </div>
          <div>
            <label>Mã vạch / SKU</label>
            <input v-model="form.code" required placeholder="VD: 8934841001234" />
          </div>
        </div>

        <div class="three-col">
          <div>
            <label>Quy cách</label>
            <input v-model="form.variant" placeholder="VD: Lốc 4 hộp" />
          </div>
          <div>
            <label>Đơn vị tính</label>
            <input v-model="form.unit" required placeholder="hộp / chai / gói" />
          </div>
          <div>
            <label>Giá bán (VNĐ)</label>
            <input v-model="form.unitPrice" type="number" min="0" step="500" required />
          </div>
        </div>

        <button type="submit" class="btn-solid full">+ Lưu mặt hàng</button>
      </form>

      <!-- Sửa nhanh SKU -->
      <div v-if="editSkuModal" class="ai-box" style="margin-top: 18px">
        <strong>Cập nhật SKU #{{ editSkuModal.id }}</strong>
        <div class="two-col">
          <div>
            <label>Mã vạch</label>
            <input v-model="editSkuModal.code" />
          </div>
          <div>
            <label>Quy cách</label>
            <input v-model="editSkuModal.variant" />
          </div>
        </div>
        <div class="two-col">
          <div>
            <label>Đơn vị</label>
            <input v-model="editSkuModal.unit" />
          </div>
          <div>
            <label>Giá bán (VNĐ)</label>
            <input v-model="editSkuModal.unitPrice" type="number" min="0" step="500" />
          </div>
        </div>
        <div class="bar-row compact">
          <button type="button" class="btn-solid" @click="saveSku">Lưu thay đổi</button>
          <button type="button" class="btn-outline" @click="editSkuModal = null">Đóng</button>
        </div>
      </div>
    </section>

    <section class="card">
      <div class="card-head">
        <div>
          <span class="badge">DANH SÁCH HÀNG HOÁ ({{ products.length }})</span>
          <h3>Bảng giá & tồn kho hiện tại</h3>
        </div>
        <button type="button" class="btn-outline" @click="loadAll">Làm mới</button>
      </div>

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
          <template v-for="p in products" :key="p.id">
            <tr v-for="s in p.skus" :key="s.id">
              <td><code>{{ s.code }}</code></td>
              <td>
                <strong>{{ p.name }}</strong>
                <div class="meta">{{ s.variant }} · {{ s.unit }}</div>
              </td>
              <td>{{ p.categoryName || '—' }}</td>
              <td><strong>{{ formatVnd(s.unitPrice) }}</strong></td>
              <td>{{ s.stock }}</td>
              <td>
                <button type="button" class="btn-sm" @click="startEditSku(s)">Sửa giá</button>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </section>
  </div>
</template>

