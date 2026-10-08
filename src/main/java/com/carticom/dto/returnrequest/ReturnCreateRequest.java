package com.carticom.dto.returnrequest;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReturnCreateRequest {

    /** What went wrong with the order — required, validated in the service so
     *  the customer always gets a plain-language message back. */
    @Size(max = 1000, message = "Your reason can be up to 1000 characters")
    private String reason;
}
