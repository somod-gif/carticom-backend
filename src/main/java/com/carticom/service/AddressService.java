package com.carticom.service;

import com.carticom.dto.address.AddressRequest;
import com.carticom.dto.address.AddressResponse;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Address;
import com.carticom.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService {

    /** The address book stops growing at 20 — plenty for real life, small enough to scan. */
    public static final int MAX_ADDRESSES_PER_CUSTOMER = 20;

    private static final int LABEL_MAX = 100;
    private static final int STREET_MAX = 255;
    private static final int CITY_MAX = 100;
    private static final int STATE_MAX = 100;
    private static final int COUNTRY_MAX = 100;
    private static final int PHONE_MAX = 30;
    private static final String DEFAULT_LABEL = "Address";

    private final AddressRepository addressRepository;

    /** One customer's saved addresses: default first, newest after that. */
    public List<AddressResponse> getMyAddresses(String customerEmail) {
        return addressRepository.findByCustomerIdOrderByCreatedAtDesc(customerEmail).stream()
                // Default first, without losing the newest-first order for the rest.
                .sorted(Comparator.comparing((Address a) -> !Boolean.TRUE.equals(a.getIsDefault())))
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Saves a new address for the customer. The first one is always the default —
     * there's nothing else to fall back to yet.
     */
    @Transactional
    public AddressResponse createAddress(String customerEmail, AddressRequest request) {
        CheckedAddress checked = check(request);

        long saved = addressRepository.countByCustomerId(customerEmail);
        if (saved >= MAX_ADDRESSES_PER_CUSTOMER) {
            throw new BadRequestException("You can save up to 20 addresses");
        }

        boolean makeDefault = saved == 0 || Boolean.TRUE.equals(request.getIsDefault());
        if (makeDefault) {
            clearOtherDefaults(customerEmail, null);
        }

        Address address = Address.builder()
                .customerId(customerEmail)
                .label(checked.label())
                .street(checked.street())
                .city(checked.city())
                .state(checked.state())
                .country(checked.country())
                .phone(checked.phone())
                .isDefault(makeDefault)
                .build();
        addressRepository.save(address);
        log.info("Address {} saved for {}", address.getId(), customerEmail);

        return mapToResponse(address);
    }

    /** Updates one of the customer's own addresses — anyone else's is a 404. */
    @Transactional
    public AddressResponse updateAddress(String customerEmail, Long id, AddressRequest request) {
        Address address = resolveOwnedAddress(customerEmail, id);
        CheckedAddress checked = check(request);

        address.setLabel(checked.label());
        address.setStreet(checked.street());
        address.setCity(checked.city());
        address.setState(checked.state());
        address.setCountry(checked.country());
        address.setPhone(checked.phone());

        // Ticking the box switches the default over; leaving it unticked keeps whatever
        // this address already was — a customer always keeps one default address.
        if (Boolean.TRUE.equals(request.getIsDefault()) && !Boolean.TRUE.equals(address.getIsDefault())) {
            clearOtherDefaults(customerEmail, address.getId());
            address.setIsDefault(true);
        }

        addressRepository.save(address);
        log.info("Address {} updated for {}", address.getId(), customerEmail);

        return mapToResponse(address);
    }

    /**
     * Deletes one of the customer's own addresses. The last one stays put, and the
     * default has to be handed over first so there's always somewhere to deliver.
     */
    @Transactional
    public void deleteAddress(String customerEmail, Long id) {
        Address address = resolveOwnedAddress(customerEmail, id);

        if (addressRepository.countByCustomerId(customerEmail) <= 1) {
            throw new BadRequestException("Keep at least one address");
        }
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            throw new BadRequestException("Make another address your default before deleting this one");
        }

        addressRepository.delete(address);
        log.info("Address {} deleted for {}", id, customerEmail);
    }

    /** Makes this address the default and clears the flag from every other one. */
    @Transactional
    public AddressResponse setDefaultAddress(String customerEmail, Long id) {
        Address address = resolveOwnedAddress(customerEmail, id);

        if (!Boolean.TRUE.equals(address.getIsDefault())) {
            clearOtherDefaults(customerEmail, address.getId());
            address.setIsDefault(true);
            addressRepository.save(address);
            log.info("Address {} is now the default for {}", id, customerEmail);
        }

        return mapToResponse(address);
    }

    /** Loads the address and confirms it belongs to this customer — not yours is a 404,
     *  never a 403, so we never reveal whether an address exists. */
    private Address resolveOwnedAddress(String customerEmail, Long id) {
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        if (!customerEmail.equals(address.getCustomerId())) {
            throw new ResourceNotFoundException("Address not found");
        }
        return address;
    }

    /** One default per customer — everyone else goes back to false. The other rows are
     *  managed entities, so they flush with the transaction. */
    private void clearOtherDefaults(String customerEmail, Long keepId) {
        addressRepository.findByCustomerIdOrderByCreatedAtDesc(customerEmail).stream()
                .filter(a -> keepId == null || !keepId.equals(a.getId()))
                .filter(a -> Boolean.TRUE.equals(a.getIsDefault()))
                .forEach(a -> a.setIsDefault(false));
    }

    /** Trims what the customer typed and enforces each field's limit in plain words. */
    private String normalize(String value, int max, String field) {
        String cleaned = value == null ? null : value.trim();
        if (cleaned != null && cleaned.isEmpty()) cleaned = null;
        if (cleaned != null && cleaned.length() > max) {
            throw new BadRequestException("Your " + field + " can be up to " + max + " characters");
        }
        return cleaned;
    }

    /** Checks the required fields and returns the cleaned-up address. */
    private CheckedAddress check(AddressRequest request) {
        String street = normalize(request.getStreet(), STREET_MAX, "street address");
        String city = normalize(request.getCity(), CITY_MAX, "city");
        String country = normalize(request.getCountry(), COUNTRY_MAX, "country");
        if (street == null) {
            throw new BadRequestException("Please add a street address");
        }
        if (city == null) {
            throw new BadRequestException("Please add a city");
        }
        if (country == null) {
            throw new BadRequestException("Please add a country");
        }

        String label = normalize(request.getLabel(), LABEL_MAX, "label");
        if (label == null) {
            label = DEFAULT_LABEL;
        }

        return new CheckedAddress(label, street, city,
                normalize(request.getState(), STATE_MAX, "state"),
                country,
                normalize(request.getPhone(), PHONE_MAX, "phone number"));
    }

    /** The address once it's trimmed and checked — everything the customer typed, cleaned up. */
    private record CheckedAddress(String label, String street, String city, String state,
                                  String country, String phone) {}

    private AddressResponse mapToResponse(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .label(address.getLabel())
                .street(address.getStreet())
                .city(address.getCity())
                .state(address.getState())
                .country(address.getCountry())
                .phone(address.getPhone())
                .isDefault(address.getIsDefault())
                .createdAt(address.getCreatedAt())
                .build();
    }
}
