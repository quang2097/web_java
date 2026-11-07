package com.supermarket.modules.catalog;

import com.supermarket.common.ApiException;
import com.supermarket.domain.*;
import com.supermarket.modules.catalog.CatalogViews.*;
import com.supermarket.modules.stock.StockService;
import com.supermarket.repository.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final SkuRepository skus;
    private final SupplierRepository suppliers;
    private final InventoryRepository inventory;
    private final StockService stock;

    public CatalogService(CategoryRepository categories, ProductRepository products, SkuRepository skus,
                          SupplierRepository suppliers, InventoryRepository inventory, StockService stock) {
        this.categories = categories; this.products = products; this.skus = skus;
        this.suppliers = suppliers; this.inventory = inventory; this.stock = stock;
    }

    public record SkuInput(String code, String variant, String unit, BigDecimal unitPrice) {}
    public record ProductInput(String name, String description, Long categoryId, String imageUrl, Boolean active, List<SkuInput> skus) {}

    /** Tồn kho theo SKU (cộng tất cả các kho). */
    @Transactional(readOnly = true)
    public Map<Long, Long> stockBySku() {
        return inventory.findAllDetailed().stream()
                .collect(Collectors.groupingBy(i -> i.sku.id, Collectors.summingLong(i -> i.quantity)));
    }

    // ---- Danh mục ----
    @Transactional(readOnly = true)
    public List<CategoryView> listCategories() {
        return categories.findAll().stream().sorted(Comparator.comparing(c -> c.name)).map(CategoryView::of).toList();
    }
    @Transactional
    public CategoryView saveCategory(Long id, String name) {
        var c = id == null ? new Category() : categories.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy danh mục."));
        if (!name.trim().equalsIgnoreCase(c.name == null ? "" : c.name) && categories.existsByNameIgnoreCase(name.trim()))
            throw ApiException.conflict("Danh mục đã tồn tại.");
        c.name = name.trim();
        return CategoryView.of(categories.save(c));
    }
    @Transactional
    public void deleteCategory(long id) {
        categories.deleteById(id);
        categories.flush(); // lỗi khoá ngoại (danh mục đang dùng) được báo ngay thành 409
    }

    // ---- Tra cứu SKU cho quầy thu ngân ----
    @Transactional(readOnly = true)
    public List<SkuView> searchSkus(String q) {
        var stockMap = stockBySku();
        return skus.search(q == null ? "" : q.trim()).stream().limit(50)
                .map(s -> SkuView.of(s, stockMap.getOrDefault(s.id, 0L))).toList();
    }
    @Transactional(readOnly = true)
    public SkuView findByCode(String code) {
        var s = skus.findByCode(code.trim()).orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm có mã " + code));
        if (!s.product.active) throw ApiException.notFound("Sản phẩm đã ngừng kinh doanh.");
        return SkuView.of(s, inventory.totalQuantity(s.id));
    }
    @Transactional(readOnly = true)
    public List<SkuView> allActiveSkus() {
        var stockMap = stockBySku();
        return skus.findAllActive().stream().map(s -> SkuView.of(s, stockMap.getOrDefault(s.id, 0L))).toList();
    }

    // ---- Sản phẩm ----
    @Transactional(readOnly = true)
    public List<ProductView> listProducts() {
        var stockMap = stockBySku();
        var skusByProduct = skus.findAllByOrderByIdAsc().stream().collect(Collectors.groupingBy(s -> s.product.id));
        return products.findAllByOrderByNameAsc().stream().map(p -> new ProductView(p.id, p.name, p.description,
                p.category == null ? null : p.category.id, p.category == null ? null : p.category.name, p.imageUrl, p.active,
                skusByProduct.getOrDefault(p.id, List.of()).stream()
                        .sorted(Comparator.comparing(s -> s.id))
                        .map(s -> SkuView.of(s, stockMap.getOrDefault(s.id, 0L))).toList())).toList();
    }

    private void apply(Product p, ProductInput in) {
        if (in.name() == null || in.name().isBlank()) throw ApiException.badRequest("Tên sản phẩm không được để trống.");
        p.name = in.name().trim();
        p.description = in.description() == null || in.description().isBlank() ? null : in.description().trim();
        p.imageUrl = in.imageUrl() == null || in.imageUrl().isBlank() ? null : in.imageUrl().trim();
        p.category = in.categoryId() == null ? null
                : categories.findById(in.categoryId()).orElseThrow(() -> ApiException.badRequest("Danh mục không hợp lệ."));
        if (in.active() != null) p.active = in.active();
    }

    private void apply(Sku s, SkuInput in) {
        if (in.code() == null || !in.code().trim().matches("[A-Za-z0-9_-]{3,64}"))
            throw ApiException.badRequest("Mã vạch/SKU phải gồm 3–64 ký tự chữ, số, '-' hoặc '_'.");
        if (in.unitPrice() == null || in.unitPrice().signum() < 0) throw ApiException.badRequest("Giá bán không hợp lệ.");
        var code = in.code().trim();
        if (!code.equals(s.code) && skus.existsByCode(code)) throw ApiException.conflict("Mã " + code + " đã tồn tại.");
        s.code = code;
        s.variant = in.variant() == null ? "" : in.variant().trim();
        s.unit = in.unit() == null || in.unit().isBlank() ? "cái" : in.unit().trim();
        s.unitPrice = in.unitPrice();
    }

    @Transactional
    public void createProduct(ProductInput in) {
        if (in.skus() == null || in.skus().isEmpty()) throw ApiException.badRequest("Sản phẩm cần ít nhất một mã SKU.");
        var p = new Product();
        apply(p, in);
        products.save(p);
        for (var si : in.skus()) addSku(p, si);
    }

    @Transactional
    public void updateProduct(long id, ProductInput in) {
        var p = products.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm."));
        apply(p, in);
    }

    @Transactional
    public void addSku(long productId, SkuInput in) {
        addSku(products.findById(productId).orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm.")), in);
    }

    private void addSku(Product p, SkuInput in) {
        var s = new Sku();
        s.product = p;
        apply(s, in);
        skus.saveAndFlush(s);
        stock.ensureInventoryRow(s); // tạo dòng tồn kho = 0 ở kho mặc định
    }

    @Transactional
    public void updateSku(long id, SkuInput in) {
        apply(skus.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy SKU.")), in);
    }

    // ---- Nhà cung cấp ----
    @Transactional(readOnly = true)
    public List<SupplierView> listSuppliers() {
        return suppliers.findAll().stream().sorted(Comparator.comparing(s -> s.name)).map(SupplierView::of).toList();
    }
    @Transactional
    public SupplierView saveSupplier(Long id, String name, String phone, String address) {
        var s = id == null ? new Supplier() : suppliers.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp."));
        if (name == null || name.isBlank()) throw ApiException.badRequest("Tên nhà cung cấp không được để trống.");
        s.name = name.trim();
        s.phone = phone == null || phone.isBlank() ? null : phone.trim();
        s.address = address == null || address.isBlank() ? null : address.trim();
        return SupplierView.of(suppliers.save(s));
    }
}