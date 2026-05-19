package com.gtnewhorizons.gametest.internal;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.gametest.api.GameTestHelper;
import com.gtnewhorizons.gametest.api.annotation.AfterBatch;
import com.gtnewhorizons.gametest.api.annotation.BeforeBatch;
import com.gtnewhorizons.gametest.api.annotation.GameTest;
import com.gtnewhorizons.gametest.api.annotation.GameTestHolder;

import cpw.mods.fml.common.discovery.ASMDataTable;

public final class GameTestRegistry {

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private static ASMDataTable asmData;

    private static final List<GameTestDefinition> ALL_TESTS = new ArrayList<>();
    private static final Map<String, List<Method>> BEFORE_BATCH_METHODS = new LinkedHashMap<>();
    private static final Map<String, List<Method>> AFTER_BATCH_METHODS = new LinkedHashMap<>();

    private GameTestRegistry() {}

    public static void setAsmData(ASMDataTable data) {
        asmData = data;
    }

    public static void discoverTests() {
        ALL_TESTS.clear();
        BEFORE_BATCH_METHODS.clear();
        AFTER_BATCH_METHODS.clear();

        if (asmData == null) {
            LOG.error("ASMDataTable not set - cannot discover tests.");
            return;
        }

        Set<ASMDataTable.ASMData> holderAnnotations = asmData.getAll(GameTestHolder.class.getName());
        if (holderAnnotations == null || holderAnnotations.isEmpty()) {
            LOG.info("No @GameTestHolder classes found.");
            return;
        }

        for (ASMDataTable.ASMData holderData : holderAnnotations) {
            String className = holderData.getClassName();
            try {
                Class<?> holderClass = Class.forName(className, false, GameTestRegistry.class.getClassLoader());
                processHolderClass(holderClass);
            } catch (ClassNotFoundException e) {
                LOG.error("Could not load @GameTestHolder class '{}'", className, e);
            }
        }

        ALL_TESTS.sort(Comparator.comparing(GameTestDefinition::getTestId));
        LOG.info(
            "Discovery complete: {} test(s) found across {} class(es).",
            ALL_TESTS.size(),
            holderAnnotations.size());
    }

    private static void processHolderClass(Class<?> clazz) {
        GameTestHolder holderAnn = clazz.getAnnotation(GameTestHolder.class);
        if (holderAnn == null) return;

        String namespace = holderAnn.value();
        String templatePrefix = holderAnn.templatePrefix();

        for (Method method : clazz.getDeclaredMethods()) {
            GameTest testAnn = method.getAnnotation(GameTest.class);
            if (testAnn != null) {
                if (!validateTestMethod(method, clazz)) continue;

                String rawTemplate = testAnn.template();
                String resolvedTemplate = resolveTemplate(namespace, templatePrefix, rawTemplate, method.getName());

                String testId = namespace + ":" + clazz.getSimpleName() + "." + method.getName();
                GameTestDefinition def = new GameTestDefinition(
                    testId,
                    method,
                    resolvedTemplate,
                    testAnn.timeoutTicks(),
                    testAnn.batch(),
                    testAnn.required(),
                    testAnn.rotation());
                ALL_TESTS.add(def);
                LOG.debug("Registered test: {}", testId);
            }

            BeforeBatch beforeAnn = method.getAnnotation(BeforeBatch.class);
            if (beforeAnn != null) {
                if (validateBatchMethod(method, clazz)) {
                    BEFORE_BATCH_METHODS.computeIfAbsent(beforeAnn.value(), k -> new ArrayList<>())
                        .add(method);
                }
            }

            AfterBatch afterAnn = method.getAnnotation(AfterBatch.class);
            if (afterAnn != null) {
                if (validateBatchMethod(method, clazz)) {
                    AFTER_BATCH_METHODS.computeIfAbsent(afterAnn.value(), k -> new ArrayList<>())
                        .add(method);
                }
            }
        }
    }

    private static String resolveTemplate(String namespace, String prefix, String rawTemplate, String methodName) {
        if (rawTemplate.isEmpty()) {
            return "";
        }
        if (rawTemplate.contains(":")) {
            return rawTemplate;
        }
        String base = prefix.isEmpty() ? rawTemplate : (prefix + "/" + rawTemplate);
        return namespace + ":" + base;
    }

    private static boolean validateTestMethod(Method method, Class<?> clazz) {
        if (!Modifier.isStatic(method.getModifiers())) {
            LOG.warn("Skipping @GameTest method '{}' in '{}': must be static.", method.getName(), clazz.getName());
            return false;
        }
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 1 || !GameTestHelper.class.isAssignableFrom(params[0])) {
            LOG.warn(
                "Skipping @GameTest method '{}' in '{}': must take exactly one GameTestHelper parameter.",
                method.getName(),
                clazz.getName());
            return false;
        }
        return true;
    }

    private static boolean validateBatchMethod(Method method, Class<?> clazz) {
        if (!Modifier.isStatic(method.getModifiers())) {
            LOG.warn(
                "Skipping @BeforeBatch/@AfterBatch method '{}' in '{}': must be static.",
                method.getName(),
                clazz.getName());
            return false;
        }
        if (method.getParameterCount() != 0) {
            LOG.warn(
                "Skipping @BeforeBatch/@AfterBatch method '{}' in '{}': must take no parameters.",
                method.getName(),
                clazz.getName());
            return false;
        }
        return true;
    }

    public static List<GameTestDefinition> getAllTests() {
        return Collections.unmodifiableList(ALL_TESTS);
    }

    public static List<GameTestDefinition> getTestsForBatch(String batchName) {
        List<GameTestDefinition> result = new ArrayList<>();
        for (GameTestDefinition def : ALL_TESTS) {
            if (def.getBatch()
                .equals(batchName)) result.add(def);
        }
        return result;
    }

    public static List<GameTestDefinition> getTestsForNamespace(String namespace) {
        List<GameTestDefinition> result = new ArrayList<>();
        for (GameTestDefinition def : ALL_TESTS) {
            if (def.getTestId()
                .startsWith(namespace + ":")) result.add(def);
        }
        return result;
    }

    public static Map<String, List<Method>> getBeforeBatchMethods() {
        return Collections.unmodifiableMap(BEFORE_BATCH_METHODS);
    }

    public static Map<String, List<Method>> getAfterBatchMethods() {
        return Collections.unmodifiableMap(AFTER_BATCH_METHODS);
    }
}
