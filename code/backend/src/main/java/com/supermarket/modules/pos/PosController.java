package com.supermarket.modules.pos;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pos")
public class PosController {
    private final PosService service;
    public PosController(PosService service) { this.service = service; }

    @PostMapping("/invoices")
    public PosService.InvoiceView create(@AuthenticationPrincipal Jwt jwt, @RequestBody PosService.CreateInvoice r) {
        return service.createInvoice(jwt.getSubject(), r);
    }
    @PostMapping("/invoices/{id}/pay")
    public PosService.InvoiceView pay(@PathVariable long id, @RequestBody PosService.PayRequest r) { return service.pay(id, r); }

    @PostMapping("/invoices/{id}/cancel")
    public PosService.InvoiceView cancel(@PathVariable long id) { return service.cancel(id); }

    @GetMapping("/invoices/{id}")
    public PosService.InvoiceView get(@PathVariable long id) { return service.get(id); }

    @GetMapping("/invoices")
    public List<PosService.InvoiceView> recent(@RequestParam(defaultValue = "50") int limit) { return service.recent(limit); }

    @GetMapping("/payment-config")
    public PosService.PaymentConfig paymentConfig() { return service.paymentConfig(); }
}