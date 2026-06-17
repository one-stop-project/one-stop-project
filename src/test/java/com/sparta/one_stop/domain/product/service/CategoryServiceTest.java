package com.sparta.one_stop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.one_stop.domain.product.dto.request.CategoryCreateRequest;
import com.sparta.one_stop.domain.product.dto.request.CategoryUpdateRequest;
import com.sparta.one_stop.domain.product.dto.response.CategoryResponse;
import com.sparta.one_stop.domain.product.dto.response.CategoryTreeResponse;
import com.sparta.one_stop.domain.product.entity.Category;
import com.sparta.one_stop.domain.product.repository.CategoryRepository;
import com.sparta.one_stop.domain.product.repository.ProductCategoryMappingRepository;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.exception.CustomException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductCategoryMappingRepository productCategoryMappingRepository;

    @InjectMocks
    private CategoryService categoryService;

    // ===== 객체 생성 헬퍼 =====

    // 실제 Category 엔티티 생성 (id는 리플렉션으로 주입, getDepth 정상 동작)
    private Category category(Long id, String name, Category parent) {
        Category c = Category.builder()
                .name(name)
                .parent(parent)
                .build();
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    // ===== 목 세팅 헬퍼 =====

    private void mockFindCategory(Category category) {
        given(categoryRepository.findById(category.getId())).willReturn(Optional.of(category));
    }

    private void mockCategoryNotFound(Long categoryId) {
        given(categoryRepository.findById(categoryId)).willReturn(Optional.empty());
    }

    private void mockAllCategories(Category... categories) {
        given(categoryRepository.findAllByOrderByIdAsc()).willReturn(List.of(categories));
    }

    private void mockSaveReturns(Category category) {
        given(categoryRepository.save(any(Category.class))).willReturn(category);
    }

    private void mockProductMappingExists(boolean exists) {
        given(productCategoryMappingRepository.existsByCategoryIdIn(anyList())).willReturn(exists);
    }

    @Nested
    @DisplayName("create - 카테고리 생성")
    class Create {

        @Test
        @DisplayName("루트 카테고리 생성 성공")
        void create_root_success() {
            // given
            given(categoryRepository.existsByParentIsNullAndName("전자기기")).willReturn(false);
            mockSaveReturns(category(1L, "전자기기", null));

            // when
            CategoryResponse response =
                    categoryService.create(new CategoryCreateRequest("전자기기", null));

            // then
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("전자기기");
            assertThat(response.parentId()).isNull();
        }

        @Test
        @DisplayName("자식 카테고리 생성 성공")
        void create_child_success() {
            // given
            Category root = category(1L, "전자기기", null);
            mockFindCategory(root);
            given(categoryRepository.existsByParentIdAndName(1L, "노트북")).willReturn(false);
            mockSaveReturns(category(2L, "노트북", root));

            // when
            CategoryResponse response =
                    categoryService.create(new CategoryCreateRequest("노트북", 1L));

            // then
            assertThat(response.id()).isEqualTo(2L);
            assertThat(response.parentId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("존재하지 않는 parentId면 CATEGORY_001 예외")
        void create_parentNotFound_throwsCategory001() {
            // given
            mockCategoryNotFound(99L);

            // when & then
            assertThatThrownBy(
                    () -> categoryService.create(new CategoryCreateRequest("노트북", 99L)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_001);
        }

        @Test
        @DisplayName("부모 깊이가 3이면 CATEGORY_003 예외")
        void create_parentAtMaxDepth_throwsCategory003() {
            // given
            Category root = category(1L, "1단", null);
            Category mid = category(2L, "2단", root);
            Category leaf = category(3L, "3단", mid);
            mockFindCategory(leaf);

            // when & then
            assertThatThrownBy(
                    () -> categoryService.create(new CategoryCreateRequest("4단", 3L)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_003);
        }

        @Test
        @DisplayName("루트에 이름이 중복되면 CATEGORY_002 예외")
        void create_rootDuplicateName_throwsCategory002() {
            // given
            given(categoryRepository.existsByParentIsNullAndName("전자기기")).willReturn(true);

            // when & then
            assertThatThrownBy(
                    () -> categoryService.create(new CategoryCreateRequest("전자기기", null)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_002);
        }

        @Test
        @DisplayName("같은 부모 아래 이름이 중복되면 CATEGORY_002 예외")
        void create_childDuplicateName_throwsCategory002() {
            // given
            Category root = category(1L, "전자기기", null);
            mockFindCategory(root);
            given(categoryRepository.existsByParentIdAndName(1L, "노트북")).willReturn(true);

            // when & then
            assertThatThrownBy(
                    () -> categoryService.create(new CategoryCreateRequest("노트북", 1L)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_002);
        }

        @Test
        @DisplayName("이름 앞뒤 공백은 제거되어 검증·저장된다 (\" 전자기기 \" → \"전자기기\")")
        void create_trimsName() {
            // given — 공백 제거된 이름으로 중복 검사·저장이 이뤄져야 한다
            given(categoryRepository.existsByParentIsNullAndName("전자기기")).willReturn(false);
            mockSaveReturns(category(1L, "전자기기", null));

            // when
            CategoryResponse response =
                    categoryService.create(new CategoryCreateRequest("  전자기기  ", null));

            // then
            assertThat(response.name()).isEqualTo("전자기기");
            verify(categoryRepository).existsByParentIsNullAndName("전자기기");
            verify(categoryRepository).save(argThat(c -> "전자기기".equals(c.getName())));
        }

        @Test
        @DisplayName("동시 생성 경합으로 DB 유니크 제약 위반 시 CATEGORY_002로 매핑된다")
        void create_dbUniqueViolation_throwsCategory002() {
            // given — 앱 레벨 검사는 통과했으나 동시 INSERT 경합으로 DB 제약이 막은 경우
            given(categoryRepository.existsByParentIsNullAndName("전자기기")).willReturn(false);
            given(categoryRepository.save(any(Category.class)))
                    .willThrow(new DataIntegrityViolationException("uk_category_parent_name"));

            // when & then
            assertThatThrownBy(
                    () -> categoryService.create(new CategoryCreateRequest("전자기기", null)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_002);
        }
    }

    @Nested
    @DisplayName("updateName - 카테고리 이름 수정")
    class UpdateName {

        @Test
        @DisplayName("이름 변경 성공")
        void updateName_success() {
            // given
            Category root = category(1L, "전자기기", null);
            Category child = category(2L, "노트북", root);
            mockFindCategory(child);
            given(categoryRepository.existsByParentIdAndNameAndIdNot(1L, "랩탑", 2L))
                    .willReturn(false);

            // when
            CategoryResponse response =
                    categoryService.updateName(2L, new CategoryUpdateRequest("랩탑"));

            // then
            assertThat(response.name()).isEqualTo("랩탑");
        }

        @Test
        @DisplayName("기존과 동일한 이름이면 중복 검증을 통과한다 (자기 자신 제외)")
        void updateName_sameName_passesUniqueCheck() {
            // given
            Category root = category(1L, "전자기기", null);
            Category child = category(2L, "노트북", root);
            mockFindCategory(child);
            given(categoryRepository.existsByParentIdAndNameAndIdNot(1L, "노트북", 2L))
                    .willReturn(false);

            // when
            CategoryResponse response =
                    categoryService.updateName(2L, new CategoryUpdateRequest("노트북"));

            // then
            assertThat(response.name()).isEqualTo("노트북");
        }

        @Test
        @DisplayName("존재하지 않는 카테고리면 CATEGORY_001 예외")
        void updateName_categoryNotFound_throwsCategory001() {
            // given
            mockCategoryNotFound(99L);

            // when & then
            assertThatThrownBy(
                    () -> categoryService.updateName(99L, new CategoryUpdateRequest("랩탑")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_001);
        }

        @Test
        @DisplayName("형제와 이름이 중복되면 CATEGORY_002 예외")
        void updateName_duplicateSiblingName_throwsCategory002() {
            // given
            Category root = category(1L, "전자기기", null);
            Category child = category(2L, "노트북", root);
            mockFindCategory(child);
            given(categoryRepository.existsByParentIdAndNameAndIdNot(1L, "태블릿", 2L))
                    .willReturn(true);

            // when & then
            assertThatThrownBy(
                    () -> categoryService.updateName(2L, new CategoryUpdateRequest("태블릿")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_002);
        }

        @Test
        @DisplayName("수정 이름 앞뒤 공백은 제거되어 검증·반영된다")
        void updateName_trimsName() {
            // given
            Category root = category(1L, "전자기기", null);
            Category child = category(2L, "노트북", root);
            mockFindCategory(child);
            given(categoryRepository.existsByParentIdAndNameAndIdNot(1L, "랩탑", 2L))
                    .willReturn(false);

            // when
            CategoryResponse response =
                    categoryService.updateName(2L, new CategoryUpdateRequest("  랩탑  "));

            // then
            assertThat(response.name()).isEqualTo("랩탑");
            verify(categoryRepository).existsByParentIdAndNameAndIdNot(1L, "랩탑", 2L);
        }

        @Test
        @DisplayName("동시 수정 경합으로 DB 유니크 제약 위반(flush) 시 CATEGORY_002로 매핑된다")
        void updateName_dbUniqueViolation_throwsCategory002() {
            // given
            Category root = category(1L, "전자기기", null);
            Category child = category(2L, "노트북", root);
            mockFindCategory(child);
            given(categoryRepository.existsByParentIdAndNameAndIdNot(1L, "태블릿", 2L))
                    .willReturn(false);
            willThrow(new DataIntegrityViolationException("uk_category_parent_name"))
                    .given(categoryRepository).flush();

            // when & then
            assertThatThrownBy(
                    () -> categoryService.updateName(2L, new CategoryUpdateRequest("태블릿")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_002);
        }
    }

    @Nested
    @DisplayName("delete - 카테고리 삭제")
    class Delete {

        @Test
        @DisplayName("하위가 없는 단일 카테고리 삭제 성공")
        void delete_single_success() {
            // given
            Category root = category(1L, "전자기기", null);
            mockFindCategory(root);
            mockAllCategories(root);
            mockProductMappingExists(false);

            // when
            categoryService.delete(1L);

            // then
            verify(categoryRepository).deleteAllByIdInBatch(List.of(1L));
        }

        @Test
        @DisplayName("자식 포함 서브트리를 자식부터 역순으로 일괄 삭제한다")
        void delete_withDescendants_success() {
            // given
            Category root = category(1L, "전자기기", null);
            Category child = category(2L, "노트북", root);
            Category grand = category(3L, "게이밍노트북", child);
            mockFindCategory(root);
            mockAllCategories(root, child, grand);
            mockProductMappingExists(false);

            // when
            categoryService.delete(1L);

            // then - 깊은 레벨(자식)부터 삭제
            verify(categoryRepository).deleteAllByIdInBatch(List.of(3L));
            verify(categoryRepository).deleteAllByIdInBatch(List.of(2L));
            verify(categoryRepository).deleteAllByIdInBatch(List.of(1L));
        }

        @Test
        @DisplayName("존재하지 않는 카테고리면 CATEGORY_001 예외")
        void delete_categoryNotFound_throwsCategory001() {
            // given
            mockCategoryNotFound(99L);

            // when & then
            assertThatThrownBy(() -> categoryService.delete(99L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_001);
        }

        @Test
        @DisplayName("자신에 상품 매핑이 존재하면 CATEGORY_004 예외")
        void delete_mappingOnSelf_throwsCategory004() {
            // given
            Category root = category(1L, "전자기기", null);
            mockFindCategory(root);
            mockAllCategories(root);
            mockProductMappingExists(true);

            // when & then
            assertThatThrownBy(() -> categoryService.delete(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_004);
            verify(categoryRepository, never()).deleteAllByIdInBatch(any());
        }

        @Test
        @DisplayName("후손에 상품 매핑이 존재하면 CATEGORY_004 예외")
        void delete_mappingOnDescendant_throwsCategory004() {
            // given
            Category root = category(1L, "전자기기", null);
            Category child = category(2L, "노트북", root);
            mockFindCategory(root);
            mockAllCategories(root, child);
            mockProductMappingExists(true);

            // when & then
            assertThatThrownBy(() -> categoryService.delete(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_004);
            verify(categoryRepository, never()).deleteAllByIdInBatch(any());
        }
    }

    @Nested
    @DisplayName("getTree - 카테고리 트리 조회")
    class GetTree {

        @Test
        @DisplayName("카테고리가 없으면 빈 리스트를 반환한다")
        void getTree_empty() {
            // given
            mockAllCategories();

            // when
            List<CategoryTreeResponse> tree = categoryService.getTree();

            // then
            assertThat(tree).isEmpty();
        }

        @Test
        @DisplayName("루트 하나만 있으면 children이 빈 노드 1개를 반환한다")
        void getTree_singleRoot() {
            // given
            Category root = category(1L, "전자기기", null);
            mockAllCategories(root);

            // when
            List<CategoryTreeResponse> tree = categoryService.getTree();

            // then
            assertThat(tree).hasSize(1);
            assertThat(tree.get(0).id()).isEqualTo(1L);
            assertThat(tree.get(0).children()).isEmpty();
        }

        @Test
        @DisplayName("3단 트리가 올바르게 조립된다")
        void getTree_threeLevels() {
            // given
            Category root = category(1L, "전자기기", null);
            Category child = category(2L, "노트북", root);
            Category grand = category(3L, "게이밍노트북", child);
            mockAllCategories(root, child, grand);

            // when
            List<CategoryTreeResponse> tree = categoryService.getTree();

            // then
            assertThat(tree).hasSize(1);
            CategoryTreeResponse rootNode = tree.get(0);
            assertThat(rootNode.id()).isEqualTo(1L);
            assertThat(rootNode.children()).hasSize(1);

            CategoryTreeResponse childNode = rootNode.children().get(0);
            assertThat(childNode.id()).isEqualTo(2L);
            assertThat(childNode.children()).hasSize(1);
            assertThat(childNode.children().get(0).id()).isEqualTo(3L);
        }

        @Test
        @DisplayName("같은 부모의 자식들은 categoryId 오름차순으로 정렬된다")
        void getTree_siblingsSortedByIdAsc() {
            // given
            Category root = category(1L, "전자기기", null);
            Category child2 = category(2L, "노트북", root);
            Category child3 = category(3L, "태블릿", root);
            // findAllByOrderByIdAsc 결과는 id 오름차순
            mockAllCategories(root, child2, child3);

            // when
            List<CategoryTreeResponse> tree = categoryService.getTree();

            // then
            List<CategoryTreeResponse> children = tree.get(0).children();
            assertThat(children).extracting(CategoryTreeResponse::id)
                    .containsExactly(2L, 3L);
        }
    }
}
