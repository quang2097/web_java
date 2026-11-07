package com.supermarket.modules.catalog;

import com.supermarket.modules.catalog.CatalogViews.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service) { this.service = service; }

    public record NameRequest(@NotBlank @Size(max = 120) String name) {}
    public record SupplierRequest(@NotBlank @Size(max = 160) String name, @Size(max = 32) String phone, @Size(max = 300) String address) {}

    // ---- Dành cho mọi nhân viên (quầy thu ngân) ----
    @GetMapping("/api/pos/categories")
    public List<CategoryView> categories() { return service.listCategories(); }

    @GetMapping("/api/pos/skus")
    public List<SkuView> searchSkus(@RequestParam(defaultValue = "") String q) { return service.searchSkus(q); }

    @GetMapping("/api/pos/skus/by-code/{code}")
    public SkuView byCode(@PathVariable String code) { return service.findByCode(code); }

    // ---- Quản lý (MANAGER, ADMIN) ----
    @PostMapping("/api/management/categories")
    public CategoryView createCategory(@Valid @RequestBody NameRequest r) { return service.saveCategory(null, r.name()); }
    @PutMapping("/api/management/categories/{id}")
    public CategoryView updateCategory(@PathVariable long id, @Valid @RequestBody NameRequest r) { return service.saveCategory(id, r.name()); }
    @DeleteMapping("/api/management/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable long id) { service.deleteCategory(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/api/management/products")
    public List<ProductView> products() { return service.listProducts(); }
    @PostMapping("/api/management/products")
    public ResponseEntity<Void> createProduct(@RequestBody CatalogService.ProductInput r) { service.createProduct(r); return ResponseEntity.noContent().build(); }
    @PutMapping("/api/management/products/{id}")
    public ResponseEntity<Void> updateProduct(@PathVariable long id, @RequestBody CatalogService.ProductInput r) { service.updateProduct(id, r); return ResponseEntity.noContent().build(); }
    @PostMapping("/api/management/products/{id}/skus")
    public ResponseEntity<Void> addSku(@PathVariable long id, @RequestBody CatalogService.SkuInput r) { service.addSku(id, r); return ResponseEntity.noContent().build(); }
    @PutMapping("/api/management/skus/{id}")
    public ResponseEntity<Void> updateSku(@PathVariable long id, @RequestBody CatalogService.SkuInput r) { service.updateSku(id, r); return ResponseEntity.noContent().build(); }

    @GetMapping("/api/management/suppliers")
    public List<SupplierView> suppliers() { return service.listSuppliers(); }
    @PostMapping("/api/management/suppliers")
    public SupplierView createSupplier(@Valid @RequestBody SupplierRequest r) { return service.saveSupplier(null, r.name(), r.phone(), r.address()); }
    @PutMapping("/api/management/suppliers/{id}")
    public SupplierView updateSupplier(@PathVariable long id, @Valid @RequestBody SupplierRequest r) { return service.saveSupplier(id, r.name(), r.phone(), r.address()); }
}