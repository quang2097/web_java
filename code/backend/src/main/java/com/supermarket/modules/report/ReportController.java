package com.supermarket.modules.report;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service) { this.service = service; }

    @GetMapping("/api/management/reports/summary")
    public ReportService.Summary summary(@RequestParam(defaultValue = "14") int days) { return service.summary(days); }

    @GetMapping("/api/management/reports/forecast")
    public List<ReportService.ForecastRow> forecast() { return service.forecast(); }

    @GetMapping("/api/pos/recommendations")
    public List<ReportService.TopProduct> recommendations(@RequestParam List<Long> skuIds) { return service.boughtTogether(skuIds); }
}