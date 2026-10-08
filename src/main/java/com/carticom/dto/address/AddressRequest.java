package com.carticom.dto.address;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the customer fills in on the address form. Required fields and length
 * limits are checked in the service so the customer always gets a
 * plain-language message back — same approach as return requests.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressRequest {

    /** Free-text nickname — e.g. "Home" or "Work". Falls back to "Address" when blank. */
    private String label;

    private String street;

    private String city;

    private String state;

    private String country;

    private String phone;

    /** True to make this the address delivery uses by default. */
    private Boolean isDefault;
}
