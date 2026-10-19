package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.models.Category;
import com.gis.servelq.models.Services;
import com.gis.servelq.repository.CategoryRepository;
import com.gis.servelq.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

/**
 * A code is the ticket prefix (A001, V001, ...), so it must be unique across
 * services, sub-services and categories together, ignoring case and spaces.
 * Services belong to one branch, so a service code only clashes with services
 * of the same branch; categories are global, so a category code clashes with
 * every service in every branch.
 */
@Component
@RequiredArgsConstructor
public class CodeUniquenessValidator {

    private final ServiceRepository serviceRepository;
    private final CategoryRepository categoryRepository;

    public static String normalize(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    /** @param excludeServiceId the service being updated, or null on create */
    public void checkServiceCode(String code, String branchId, String excludeServiceId) {
        String normalized = requireCode(code);

        for (Services s : serviceRepository.findByNormalizedCodeAndBranchId(normalized, branchId)) {
            if (!Objects.equals(s.getId(), excludeServiceId)) {
                throw clash(normalized, isSubService(s) ? "sub-service" : "service", s.getName());
            }
        }
        for (Category c : categoryRepository.findByNormalizedCode(normalized)) {
            throw clash(normalized, "category", c.getName());
        }
    }

    /** @param excludeCategoryId the category being updated, or null on create */
    public void checkCategoryCode(String code, String excludeCategoryId) {
        String normalized = requireCode(code);

        for (Category c : categoryRepository.findByNormalizedCode(normalized)) {
            if (!Objects.equals(c.getId(), excludeCategoryId)) {
                throw clash(normalized, "category", c.getName());
            }
        }
        for (Services s : serviceRepository.findByNormalizedCode(normalized)) {
            throw clash(normalized, isSubService(s) ? "sub-service" : "service", s.getName());
        }
    }

    private static String requireCode(String code) {
        String normalized = normalize(code);
        if (normalized == null || normalized.isEmpty()) {
            throw new BusinessException("Prefix is required");
        }
        return normalized;
    }

    private static boolean isSubService(Services s) {
        return s.getParentId() != null && !s.getParentId().isBlank();
    }

    private static BusinessException clash(String code, String kind, String name) {
        return new BusinessException(
                "Prefix \"" + code + "\" is already used by the " + kind + " \"" + name
                        + "\". Prefixes must be unique across services, sub-services and categories.");
    }
}
