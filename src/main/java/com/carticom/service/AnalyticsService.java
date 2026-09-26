package com.carticom.service;

import com.carticom.dto.analytics.DashboardResponse;
import com.carticom.dto.analytics.RevenueByDay;
import com.carticom.dto.analytics.TopProduct;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Product;
import com.carticom.model.Store;
import com.carticom.model.User;
import com.carticom.repository.OrderRepository;
import com.carticom.repository.ProductRepository;
import com.carticom.repository.CustomerRepository;
import com.carticom.repository.StoreRepository;
import com.carticom.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import com.carticom.model.OrderChannel;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final UserRepository userRepository;

    public DashboardResponse getDashboard(String sellerEmail) {
        Store store = getStoreBySeller(sellerEmail);

        BigDecimal totalRevenue = orderRepository.getTotalPaidRevenue(store.getId());
        Long totalOrders = orderRepository.countOrders(store.getId());
        Long deliveredOrders = orderRepository.countDeliveredOrders(store.getId());
        Long totalProducts = productRepository.countActiveProducts(store.getId());
        Long totalCustomers = customerRepository.countCustomers(store.getId());

        List<RevenueByDay> revenueChart = generateRevenueChart(store.getId());
        List<TopProduct> topProducts = getTopProducts(store.getId());
        Map<String, BigDecimal> revenueByChannel = getRevenueByChannel(store.getId());

        return DashboardResponse.builder()
                .totalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                .totalOrders(totalOrders != null ? totalOrders : 0L)
                .deliveredOrders(deliveredOrders != null ? deliveredOrders : 0L)
                .totalProducts(totalProducts != null ? totalProducts : 0L)
                .totalCustomers(totalCustomers != null ? totalCustomers : 0L)
                .revenueChart(revenueChart)
                .topProducts(topProducts)
                .revenueByChannel(revenueByChannel)
                .build();
    }

    private Map<String, BigDecimal> getRevenueByChannel(Long storeId) {
        Map<String, BigDecimal> channelRevenue = new LinkedHashMap<>();
        for (OrderChannel channel : OrderChannel.values()) {
            BigDecimal revenue = orderRepository.getTotalRevenueByChannel(storeId, channel);
            channelRevenue.put(channel.name(), revenue != null ? revenue : BigDecimal.ZERO);
        }
        return channelRevenue;
    }

    private List<RevenueByDay> generateRevenueChart(Long storeId) {
        List<RevenueByDay> chart = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd");

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            BigDecimal revenue = orderRepository.getRevenueBetweenDates(
                    storeId,
                    date.atStartOfDay(),
                    date.plusDays(1).atStartOfDay()
            );
            chart.add(RevenueByDay.builder()
                    .date(date.format(formatter))
                    .revenue(revenue != null ? revenue : BigDecimal.ZERO)
                    .orderCount(0L)
                    .build());
        }
        return chart;
    }

    private List<TopProduct> getTopProducts(Long storeId) {
        return productRepository.findByStoreId(storeId).stream()
                .sorted(Comparator.comparing(Product::getSoldCount).reversed())
                .limit(5)
                .map(p -> TopProduct.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .soldCount(p.getSoldCount())
                        .revenue(p.getPrice().multiply(BigDecimal.valueOf(p.getSoldCount())))
                        .imageUrl(p.getImageUrl())
                        .build())
                .collect(Collectors.toList());
    }

    private Store getStoreBySeller(String sellerEmail) {
        return storeAccessService.resolveStore(sellerEmail);
    }
}
