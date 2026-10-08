package com.carticom.service;

import com.carticom.dto.store.StoreResponse;
import com.carticom.dto.store.UpdateStoreBrandingRequest;
import com.carticom.exception.BadRequestException;
import com.carticom.model.Store;
import com.carticom.model.User;
import com.carticom.repository.StoreRepository;
import com.carticom.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contract of PUT /api/v1/stores/{id}/branding: partial updates, clearing with an
 * empty string, and rejection of unsafe custom CSS or unknown section names.
 */
class StoreServiceBrandingTest {

    private StoreAccessService storeAccessService;
    private Store store;
    private StoreService service;

    @BeforeEach
    void setUp() {
        StoreRepository storeRepository = mock(StoreRepository.class);
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> invocation.getArgument(0));
        storeAccessService = mock(StoreAccessService.class);

        User seller = new User();
        seller.setEmail("seller@carticom.cv");
        store = new Store();
        store.setId(7L);
        store.setSlug("acme");
        store.setName("Acme");
        store.setSeller(seller);
        store.setPrimaryColor("#4f46e5");

        when(storeAccessService.resolveStore("seller@carticom.cv")).thenReturn(store);

        service = new StoreService(storeRepository, mock(UserRepository.class),
                storeAccessService, new ObjectMapper());
    }

    @Test
    void appliesOnlyTheFieldsThePayloadCarries() {
        UpdateStoreBrandingRequest request = new UpdateStoreBrandingRequest();
        request.setSecondaryColor("#111111");
        request.setSeoTitle("Acme - the shop");

        StoreResponse response = service.updateBranding("seller@carticom.cv", 7L, request);

        assertEquals("#111111", response.getSecondaryColor());
        assertEquals("Acme - the shop", response.getSeoTitle());
        assertEquals("#4f46e5", response.getPrimaryColor(), "unset fields must be left alone");
        assertNull(response.getAnnouncementBar());
        assertTrue(response.getUpdatedAt() != null, "updatedAt must be stamped");
    }

    @Test
    void emptyStringClearsTheFreeTextFields() {
        store.setAnnouncementBar("Sale!");
        store.setCustomCss(".hero { color: red }");

        UpdateStoreBrandingRequest request = new UpdateStoreBrandingRequest();
        request.setAnnouncementBar("");
        request.setCustomCss("");

        StoreResponse response = service.updateBranding("seller@carticom.cv", 7L, request);

        assertNull(response.getAnnouncementBar());
        assertNull(response.getCustomCss());
    }

    @Test
    void rejectsCustomCssThatCouldEscapeTheStyleBlock() {
        UpdateStoreBrandingRequest request = new UpdateStoreBrandingRequest();
        request.setCustomCss(".hero { color: red } </style><script>alert(1)</script>");

        assertThrows(BadRequestException.class,
                () -> service.updateBranding("seller@carticom.cv", 7L, request));

        request.setCustomCss("@import url(https://evil.example/x.css);");
        assertThrows(BadRequestException.class,
                () -> service.updateBranding("seller@carticom.cv", 7L, request));
    }

    @Test
    void acceptsSafeCustomCss() {
        UpdateStoreBrandingRequest request = new UpdateStoreBrandingRequest();
        request.setCustomCss(".hero { color: #4f46e5; font-family: Inter, sans-serif }");

        StoreResponse response = service.updateBranding("seller@carticom.cv", 7L, request);

        assertEquals(".hero { color: #4f46e5; font-family: Inter, sans-serif }",
                response.getCustomCss());
    }

    @Test
    void onlyWhitelistedSectionsCanBeConfigured() {
        UpdateStoreBrandingRequest request = new UpdateStoreBrandingRequest();
        request.setSectionConfig("[\"hero\",\"showcase\",\"testimonials\"]");
        assertEquals("[\"hero\",\"showcase\",\"testimonials\"]",
                service.updateBranding("seller@carticom.cv", 7L, request).getSectionConfig());

        request.setSectionConfig("[\"evil-section\"]");
        assertThrows(BadRequestException.class,
                () -> service.updateBranding("seller@carticom.cv", 7L, request));

        request.setSectionConfig("{\"hero\":true}");
        assertThrows(BadRequestException.class,
                () -> service.updateBranding("seller@carticom.cv", 7L, request));

        request.setSectionConfig("not json at all");
        assertThrows(BadRequestException.class,
                () -> service.updateBranding("seller@carticom.cv", 7L, request));
    }

    @Test
    void refusesToTouchAnotherUsersStore() {
        when(storeAccessService.resolveStore("seller@carticom.cv")).thenThrow(
                new com.carticom.exception.ResourceNotFoundException("Store not found"));

        UpdateStoreBrandingRequest request = new UpdateStoreBrandingRequest();
        request.setSeoTitle("nope");

        assertThrows(com.carticom.exception.ResourceNotFoundException.class,
                () -> service.updateBranding("seller@carticom.cv", 7L, request));
    }
}
