package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.models.Category;
import com.gis.servelq.models.Services;
import com.gis.servelq.repository.CategoryRepository;
import com.gis.servelq.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * A prefix (ticket letter) may be used by only one service, sub-service or category.
 */
@ExtendWith(MockitoExtension.class)
class CodeUniquenessValidatorTest {

    @Mock private ServiceRepository serviceRepository;
    @Mock private CategoryRepository categoryRepository;

    private CodeUniquenessValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CodeUniquenessValidator(serviceRepository, categoryRepository);
        lenient().when(serviceRepository.findByNormalizedCode(anyString())).thenReturn(List.of());
        lenient().when(serviceRepository.findByNormalizedCodeAndBranchId(anyString(), anyString())).thenReturn(List.of());
        lenient().when(categoryRepository.findByNormalizedCode(anyString())).thenReturn(List.of());
    }

    private static Category category(String id, String code, String name) {
        Category c = new Category();
        c.setId(id);
        c.setCode(code);
        c.setName(name);
        return c;
    }

    private static Services service(String id, String code, String name, String parentId) {
        Services s = new Services();
        s.setId(id);
        s.setCode(code);
        s.setName(name);
        s.setParentId(parentId);
        s.setBranchId("b1");
        return s;
    }

    @Test
    void serviceCannotTakeACategoryPrefixInAnyCase() {
        when(categoryRepository.findByNormalizedCode("V")).thenReturn(List.of(category("c1", "V", "VIP")));

        assertThatThrownBy(() -> validator.checkServiceCode(" v ", "b1", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Prefix \"V\" is already used by the category \"VIP\"");
    }

    @Test
    void categoryCannotTakeASubServicePrefix() {
        when(serviceRepository.findByNormalizedCode("V"))
                .thenReturn(List.of(service("s2", "v", "Visa renewal", "s1")));

        assertThatThrownBy(() -> validator.checkCategoryCode("V", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sub-service \"Visa renewal\"");
    }

    @Test
    void serviceCannotTakeAnotherServicePrefixInTheBranch() {
        when(serviceRepository.findByNormalizedCodeAndBranchId("A", "b1"))
                .thenReturn(List.of(service("s1", "A", "Pensions", null)));

        assertThatThrownBy(() -> validator.checkServiceCode("a", "b1", null))
                .hasMessageContaining("service \"Pensions\"");
    }

    @Test
    void updatingKeepsItsOwnPrefix() {
        when(serviceRepository.findByNormalizedCodeAndBranchId("A", "b1"))
                .thenReturn(List.of(service("s1", "A", "Pensions", null)));
        when(categoryRepository.findByNormalizedCode("V")).thenReturn(List.of(category("c1", "V", "VIP")));

        assertThatCode(() -> validator.checkServiceCode("A", "b1", "s1")).doesNotThrowAnyException();
        assertThatCode(() -> validator.checkCategoryCode("v", "c1")).doesNotThrowAnyException();
    }

    @Test
    void blankPrefixIsRefused() {
        assertThatThrownBy(() -> validator.checkCategoryCode("  ", null)).hasMessage("Prefix is required");
    }
}
