package com.carticom.controller;

import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.ShippingMethod;
import com.carticom.model.ShippingZone;
import com.carticom.model.Store;
import com.carticom.repository.ShippingMethodRepository;
import com.carticom.repository.ShippingZoneRepository;
import com.carticom.service.StoreAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/shipping")
@RequiredArgsConstructor
@Tag(name = "Shipping", description = "Shipping zones, methods and rate calculation")
public class ShippingController {

    private final ShippingZoneRepository zoneRepository;
    private final ShippingMethodRepository methodRepository;
    private final StoreAccessService storeAccessService;

    public record ZoneResponse(Long id, Long storeId, String name, String countries, String regions,
                               BigDecimal baseRate, BigDecimal perKgRate, Boolean isActive) {}

    public record ZoneRequest(Long storeId, String name, String countries, String regions,
                              BigDecimal baseRate, BigDecimal perKgRate, Boolean isActive) {}

    public record MethodResponse(Long id, Long storeId, String name, String description,
                                 BigDecimal price, Integer estimatedDays, Boolean isActive) {}

    public record MethodRequest(Long storeId, String name, String description,
                                BigDecimal price, Integer estimatedDays, Boolean isActive) {}

    public record RateRequest(Long storeId, BigDecimal weight, String country, String state, String zipCode) {}

    @GetMapping("/zones/{storeId}")
    @Operation(summary = "List shipping zones for a store")
    public ResponseEntity<List<ZoneResponse>> getZones(Authentication authentication,
                                                       @PathVariable Long storeId) {
        requireOwnStore(authentication.getName(), storeId);
        return ResponseEntity.ok(
                zoneRepository.findByStoreIdOrderByIdAsc(storeId).stream()
                        .map(this::toZoneResponse)
                        .toList());
    }

    @PostMapping("/zones")
    @Operation(summary = "Create a shipping zone")
    public ResponseEntity<ZoneResponse> createZone(Authentication authentication,
                                                   @RequestBody ZoneRequest request) {
        Store store = requireOwnStore(authentication.getName(), request.storeId());
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("Zone name is required");
        }
        ShippingZone zone = ShippingZone.builder()
                .store(store)
                .name(request.name())
                .countries(request.countries())
                .regions(request.regions())
                .baseRate(request.baseRate() != null ? request.baseRate() : BigDecimal.ZERO)
                .perKgRate(request.perKgRate())
                .isActive(request.isActive() != null ? request.isActive() : true)
                .build();
        zoneRepository.save(zone);
        return ResponseEntity.ok(toZoneResponse(zone));
    }

    @PutMapping("/zones/{id}")
    @Operation(summary = "Update a shipping zone")
    public ResponseEntity<ZoneResponse> updateZone(Authentication authentication,
                                                   @PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        ShippingZone zone = requireOwnZone(authentication.getName(), id);
        String name = str(body.get("name"));
        if (name != null && !name.isBlank()) zone.setName(name);
        if (body.containsKey("countries")) zone.setCountries(str(body.get("countries")));
        if (body.containsKey("regions")) zone.setRegions(str(body.get("regions")));
        BigDecimal baseRate = decimal(body.get("baseRate"));
        if (baseRate != null) zone.setBaseRate(baseRate);
        BigDecimal perKgRate = decimal(body.get("perKgRate"));
        if (perKgRate != null) zone.setPerKgRate(perKgRate);
        if (body.get("isActive") instanceof Boolean active) zone.setIsActive(active);
        zoneRepository.save(zone);
        return ResponseEntity.ok(toZoneResponse(zone));
    }

    @DeleteMapping("/zones/{id}")
    @Operation(summary = "Delete a shipping zone")
    public ResponseEntity<Void> deleteZone(Authentication authentication, @PathVariable Long id) {
        zoneRepository.delete(requireOwnZone(authentication.getName(), id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/methods/{storeId}")
    @Operation(summary = "List shipping methods for a store")
    public ResponseEntity<List<MethodResponse>> getMethods(Authentication authentication,
                                                           @PathVariable Long storeId) {
        requireOwnStore(authentication.getName(), storeId);
        return ResponseEntity.ok(
                methodRepository.findByStoreIdOrderByIdAsc(storeId).stream()
                        .map(this::toMethodResponse)
                        .toList());
    }

    @PostMapping("/methods")
    @Operation(summary = "Create a shipping method")
    public ResponseEntity<MethodResponse> createMethod(Authentication authentication,
                                                       @RequestBody MethodRequest request) {
        Store store = requireOwnStore(authentication.getName(), request.storeId());
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("Method name is required");
        }
        ShippingMethod method = ShippingMethod.builder()
                .store(store)
                .name(request.name())
                .description(request.description())
                .price(request.price() != null ? request.price() : BigDecimal.ZERO)
                .estimatedDays(request.estimatedDays())
                .isActive(request.isActive() != null ? request.isActive() : true)
                .build();
        methodRepository.save(method);
        return ResponseEntity.ok(toMethodResponse(method));
    }

    @PutMapping("/methods/{id}")
    @Operation(summary = "Update a shipping method")
    public ResponseEntity<MethodResponse> updateMethod(Authentication authentication,
                                                       @PathVariable Long id,
                                                       @RequestBody Map<String, Object> body) {
        ShippingMethod method = requireOwnMethod(authentication.getName(), id);
        String name = str(body.get("name"));
        if (name != null && !name.isBlank()) method.setName(name);
        if (body.containsKey("description")) method.setDescription(str(body.get("description")));
        BigDecimal price = decimal(body.get("price"));
        if (price != null) method.setPrice(price);
        if (body.get("estimatedDays") instanceof Number days) method.setEstimatedDays(days.intValue());
        if (body.get("isActive") instanceof Boolean active) method.setIsActive(active);
        methodRepository.save(method);
        return ResponseEntity.ok(toMethodResponse(method));
    }

    @DeleteMapping("/methods/{id}")
    @Operation(summary = "Delete a shipping method")
    public ResponseEntity<Void> deleteMethod(Authentication authentication, @PathVariable Long id) {
        methodRepository.delete(requireOwnMethod(authentication.getName(), id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rates")
    @Operation(summary = "Calculate shipping rates",
            description = "Returns active methods for the store, adjusted by matching zone rates")
    public ResponseEntity<Map<String, Object>> calculateRates(Authentication authentication,
                                                              @RequestBody RateRequest request) {
        if (request.storeId() == null) {
            throw new BadRequestException("storeId is required");
        }
        Store store = requireOwnStore(authentication.getName(), request.storeId());

        ShippingZone zone = zoneRepository.findByStoreIdOrderByIdAsc(store.getId()).stream()
                .filter(z -> Boolean.TRUE.equals(z.getIsActive()))
                .filter(z -> request.country() == null || matches(z.getCountries(), request.country()))
                .findFirst()
                .orElse(null);

        List<Map<String, Object>> methods = new ArrayList<>();
        for (ShippingMethod method : methodRepository.findByStoreIdOrderByIdAsc(store.getId())) {
            if (!Boolean.TRUE.equals(method.getIsActive())) continue;
            BigDecimal price = method.getPrice() != null ? method.getPrice() : BigDecimal.ZERO;
            if (zone != null) {
                price = price.add(zone.getBaseRate() != null ? zone.getBaseRate() : BigDecimal.ZERO);
                if (request.weight() != null && zone.getPerKgRate() != null) {
                    price = price.add(zone.getPerKgRate().multiply(request.weight()));
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("methodId", method.getId());
            m.put("name", method.getName());
            m.put("description", method.getDescription());
            m.put("price", price);
            m.put("estimatedDays", method.getEstimatedDays());
            methods.add(m);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("destination", (request.country() != null ? request.country() : "")
                + (request.state() != null && !request.state().isBlank() ? "/" + request.state() : ""));
        res.put("weight", request.weight());
        res.put("methods", methods);
        return ResponseEntity.ok(res);
    }

    private boolean matches(String csv, String country) {
        if (csv == null || csv.isBlank()) return true;
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .anyMatch(c -> c.equalsIgnoreCase(country));
    }

    private Store requireOwnStore(String email, Long storeId) {
        Store store = storeAccessService.resolveStore(email);
        if (storeId != null && !store.getId().equals(storeId)) {
            throw new ResourceNotFoundException("Store not found");
        }
        return store;
    }

    private ShippingZone requireOwnZone(String email, Long id) {
        ShippingZone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipping zone not found"));
        if (!zone.getStore().getId().equals(storeAccessService.resolveStore(email).getId())) {
            throw new ResourceNotFoundException("Shipping zone not found");
        }
        return zone;
    }

    private ShippingMethod requireOwnMethod(String email, Long id) {
        ShippingMethod method = methodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipping method not found"));
        if (!method.getStore().getId().equals(storeAccessService.resolveStore(email).getId())) {
            throw new ResourceNotFoundException("Shipping method not found");
        }
        return method;
    }

    private ZoneResponse toZoneResponse(ShippingZone z) {
        return new ZoneResponse(z.getId(), z.getStore().getId(), z.getName(), z.getCountries(),
                z.getRegions(), z.getBaseRate(), z.getPerKgRate(),
                z.getIsActive() != null ? z.getIsActive() : true);
    }

    private MethodResponse toMethodResponse(ShippingMethod m) {
        return new MethodResponse(m.getId(), m.getStore().getId(), m.getName(), m.getDescription(),
                m.getPrice(), m.getEstimatedDays(),
                m.getIsActive() != null ? m.getIsActive() : true);
    }

    private String str(Object v) {
        return v != null ? String.valueOf(v) : null;
    }

    private BigDecimal decimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
