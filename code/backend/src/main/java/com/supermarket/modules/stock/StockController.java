package com.supermarket.modules.stock;

import com.supermarket.modules.catalog.CatalogViews.WarehouseView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management")
public class StockController {
    private final StockService service;
    public StockController(StockService service) { this.service = service; }

    public record MinRequest(@Min(0) int minQuantity) {}
    public record WarehouseRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,32}") String code, @NotBlank @Size(max = 120) String name) {}

    @GetMapping("/inventory")
    public List<StockService.InventoryView> inventory() { return service.listInventory(); }

    @GetMapping("/inventory/low-stock")
    public List<StockService.InventoryView> lowStock() { return service.lowStock(); }

    @PutMapping("/inventory/{id}/min-quantity")
    public ResponseEntity<Void> min(@PathVariable long id, @Valid @RequestBody MinRequest r) {
        service.setMinQuantity(id, r.minQuantity());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/warehouses")
    public List<WarehouseView> warehouses() { return service.listWarehouses(); }
    @PostMapping("/warehouses")
    public WarehouseView createWarehouse(@Valid @RequestBody WarehouseRequest r) { return service.createWarehouse(r.code(), r.name()); }

    @GetMapping("/imports")
    public List<StockService.ImportView> imports() { return service.recentImports(); }
    @PostMapping("/imports")
    public StockService.ImportView createImport(@AuthenticationPrincipal Jwt jwt, @RequestBody StockService.ImportInput r) {
        return service.createImport(jwt.getSubject(), r);
    }
}