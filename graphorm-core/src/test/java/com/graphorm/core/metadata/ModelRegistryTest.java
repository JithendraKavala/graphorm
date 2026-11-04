package com.graphorm.core.metadata;

import com.graphorm.core.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ModelRegistry Unit Tests")
class ModelRegistryTest {

    @Test
    @DisplayName("Should create empty registry with size 0")
    void shouldCreateEmptyRegistry() {
        ModelRegistry registry = ModelRegistry.builder().build();

        assertThat(registry.isEmpty()).isTrue();
        assertThat(registry.size()).isZero();
        assertThat(registry.getAllModels()).isEmpty();
        assertThat(registry.getAllViewModels()).isEmpty();
        assertThat(registry.getAllViews()).isEmpty();
        assertThat(registry.getAllCompositeTypes()).isEmpty();
        assertThat(registry.getAllEnums()).isEmpty();
        assertThat(registry.getAllEntities()).isEmpty();
    }

    @Test
    @DisplayName("Should register and retrieve models, views, composite types, and enums")
    void shouldRegisterAndRetrieveEntities() {
        ModelMetadata studentModel = ModelMetadata.builder("Student").build();
        ViewModelMetadata studentDashboard = ViewModelMetadata.builder("StudentDashboard").build();
        CompositeTypeMetadata address = CompositeTypeMetadata.builder("Address").build();
        EnumMetadata role = EnumMetadata.builder("Role").values("ADMIN", "STUDENT").build();

        ModelRegistry registry = ModelRegistry.builder()
                .addModel(studentModel)
                .addViewModel(studentDashboard)
                .addCompositeType(address)
                .addEnum(role)
                .build();

        assertThat(registry.size()).isEqualTo(3); // 3 entities (Student, StudentDashboard, Address)
        assertThat(registry.isEmpty()).isFalse();

        // Model lookups
        assertThat(registry.containsModel("Student")).isTrue();
        assertThat(registry.findModel("Student")).contains(studentModel);
        assertThat(registry.getModel("Student")).isEqualTo(studentModel);

        // View lookups
        assertThat(registry.containsViewModel("StudentDashboard")).isTrue();
        assertThat(registry.findViewModel("StudentDashboard")).contains(studentDashboard);
        assertThat(registry.getViewModel("StudentDashboard")).isEqualTo(studentDashboard);

        // Composite type lookups
        assertThat(registry.containsCompositeType("Address")).isTrue();
        assertThat(registry.findCompositeType("Address")).contains(address);
        assertThat(registry.getCompositeType("Address")).isEqualTo(address);

        // Enum lookups
        assertThat(registry.containsEnum("Role")).isTrue();
        assertThat(registry.findEnum("Role")).contains(role);
        assertThat(registry.getEnum("Role")).isEqualTo(role);

        // Generic entity lookups
        assertThat(registry.containsEntity("Student")).isTrue();
        assertThat(registry.containsEntity("StudentDashboard")).isTrue();
        assertThat(registry.containsEntity("Address")).isTrue();
        assertThat(registry.getEntity("Student")).isEqualTo(studentModel);
        assertThat(registry.getEntity("StudentDashboard")).isEqualTo(studentDashboard);
        assertThat(registry.getEntity("Address")).isEqualTo(address);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException with descriptive message and error code")
    void shouldThrowEntityNotFoundException() {
        ModelRegistry registry = ModelRegistry.builder().build();

        assertThatThrownBy(() -> registry.getModel("MissingModel"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Model 'MissingModel' not found in ModelRegistry")
                .matches(e -> "ERR_ENTITY_NOT_FOUND".equals(((EntityNotFoundException) e).getErrorCode()))
                .matches(e -> "MissingModel".equals(((EntityNotFoundException) e).getEntityName()));

        assertThatThrownBy(() -> registry.getViewModel("MissingView"))
                .isInstanceOf(EntityNotFoundException.class);

        assertThatThrownBy(() -> registry.getCompositeType("MissingType"))
                .isInstanceOf(EntityNotFoundException.class);

        assertThatThrownBy(() -> registry.getEnum("MissingEnum"))
                .isInstanceOf(EntityNotFoundException.class);

        assertThatThrownBy(() -> registry.getEntity("MissingEntity"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Should reject duplicate entity names across models, views, and composite types")
    void shouldRejectDuplicateEntityNames() {
        ModelMetadata student = ModelMetadata.builder("Student").build();
        ViewModelMetadata studentView = ViewModelMetadata.builder("Student").build();

        assertThatThrownBy(() -> ModelRegistry.builder()
                .addModel(student)
                .addViewModel(studentView)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate entity declaration: 'Student' is already registered");

        EnumMetadata e1 = EnumMetadata.builder("Status").values("A").build();
        EnumMetadata e2 = EnumMetadata.builder("Status").values("B").build();
        assertThatThrownBy(() -> ModelRegistry.builder()
                .addEnum(e1)
                .addEnum(e2)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate enum declaration: 'Status' is already registered");
    }

    @Test
    @DisplayName("Should ensure all returned collections are unmodifiable")
    void shouldEnsureCollectionsAreUnmodifiable() {
        ModelRegistry registry = ModelRegistry.builder()
                .addModel(ModelMetadata.builder("User").build())
                .addViewModel(ViewModelMetadata.builder("UserView").build())
                .addCompositeType(CompositeTypeMetadata.builder("Profile").build())
                .addEnum(EnumMetadata.builder("Role").values("ADMIN").build())
                .build();

        assertThatThrownBy(() -> registry.getAllModels().add(ModelMetadata.builder("Hacked").build()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> registry.getAllViewModels().add(ViewModelMetadata.builder("Hacked").build()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> registry.getAllCompositeTypes().add(CompositeTypeMetadata.builder("Hacked").build()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> registry.getAllEnums().add(EnumMetadata.builder("Hacked").values("X").build()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> registry.getAllEntities().add(ModelMetadata.builder("Hacked").build()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should support thread-safe concurrent reads across multiple threads")
    void shouldSupportConcurrentReads() throws InterruptedException {
        ModelMetadata model = ModelMetadata.builder("Product").build();
        ViewModelMetadata view = ViewModelMetadata.builder("ProductCatalog").build();
        ModelRegistry registry = ModelRegistry.builder()
                .addModel(model)
                .addViewModel(view)
                .build();

        int threadCount = 30;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCounter = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        if (registry.getModel("Product").equals(model) &&
                                registry.getViewModel("ProductCatalog").equals(view)) {
                            successCounter.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    // unexpected error
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCounter.get()).isEqualTo(threadCount * operationsPerThread);
    }
}
