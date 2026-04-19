package com.erise.ai.backend.modules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class AiAdminDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void promptTemplateCreateRequestRequiresCoreFields() {
        var violations = validator.validate(new AiPromptTemplateCreateRequest("", "", "", "", null, Boolean.TRUE));
        assertFalse(violations.isEmpty());
    }

    @Test
    void promptTemplateStatusRequestRequiresEnabledFlag() {
        var violations = validator.validate(new AiPromptTemplateStatusRequest(null));
        assertTrue(violations.stream().anyMatch((violation) -> "enabled".equals(violation.getPropertyPath().toString())));
    }
}
