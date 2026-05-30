package com.gtnewhorizons.horizonqa.internal;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.AfterBatch;
import com.gtnewhorizons.horizonqa.api.annotation.BeforeBatch;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.internal.InvalidBatchHook.HookPhase;

import cpw.mods.fml.common.discovery.ASMDataTable;

public final class GameTestRegistry {

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private static final String KIND_DISCOVERY_ERROR = "DISCOVERY_ERROR";
    private static final String KIND_DUPLICATE_TEST_ID = "DUPLICATE_TEST_ID";

    private static final String DEFAULT_BATCH_RENDER_NAME = "default";

    private static final Comparator<Method> METHOD_ORDER = Comparator.comparing(
        (Method m) -> m.getDeclaringClass()
            .getName())
        .thenComparing(Method::getName);

    private static ASMDataTable asmData;

    private static final List<GameTestDefinition> ALL_TESTS = new ArrayList<>();
    private static final Map<String, List<Method>> BEFORE_BATCH_METHODS = new LinkedHashMap<>();
    private static final Map<String, List<Method>> AFTER_BATCH_METHODS = new LinkedHashMap<>();
    private static DiscoveryResult lastDiscoveryResult = emptyResult();

    private GameTestRegistry() {}

    public static void setAsmData(ASMDataTable data) {
        asmData = data;
    }

    public static DiscoveryResult discoverTests() {
        ALL_TESTS.clear();
        BEFORE_BATCH_METHODS.clear();
        AFTER_BATCH_METHODS.clear();

        DiscoveryCollector collector = new DiscoveryCollector();

        if (asmData == null) {
            DiscoveryIssue issue = issue(
                "discovery:asmData:missing",
                KIND_DISCOVERY_ERROR,
                "ASMDataTable not set - cannot discover tests.");
            collector.issues.add(issue);
            LOG.error(issue.message());
            return publish(collector, Collections.emptySet(), 0);
        }

        Set<ASMDataTable.ASMData> holderAnnotations = asmData.getAll(GameTestHolder.class.getName());
        if (holderAnnotations == null || holderAnnotations.isEmpty()) {
            LOG.info("No @GameTestHolder classes found.");
            return publish(collector, Collections.emptySet(), 0);
        }

        for (ASMDataTable.ASMData holderData : holderAnnotations) {
            String className = holderData.getClassName();
            try {
                Class<?> holderClass = Class.forName(className, false, GameTestRegistry.class.getClassLoader());
                processHolderClass(holderClass, collector);
            } catch (ClassNotFoundException e) {
                DiscoveryIssue issue = issue(
                    "discovery:holderLoad:" + className,
                    KIND_DISCOVERY_ERROR,
                    "Could not load @GameTestHolder class '" + className + "': " + e.getMessage());
                collector.issues.add(issue);
                LOG.error("Could not load @GameTestHolder class '{}'", className, e);
            }
        }

        Set<String> duplicateIds = findDuplicates(collector);
        return publish(collector, duplicateIds, holderAnnotations.size());
    }

    private static DiscoveryResult publish(DiscoveryCollector collector, Set<String> duplicateIds,
        int holderAnnotationCount) {

        List<GameTestDefinition> validTests = new ArrayList<>();
        for (GameTestDefinition def : collector.validTests) {
            if (!duplicateIds.contains(def.getTestId())) {
                validTests.add(def);
            }
        }
        validTests.sort(Comparator.comparing(GameTestDefinition::getTestId));

        sortHookMap(collector.beforeBatchMethods);
        sortHookMap(collector.afterBatchMethods);

        ALL_TESTS.addAll(validTests);
        copyHookMap(collector.beforeBatchMethods, BEFORE_BATCH_METHODS);
        copyHookMap(collector.afterBatchMethods, AFTER_BATCH_METHODS);

        lastDiscoveryResult = new DiscoveryResult(
            immutableList(validTests),
            immutableHookMap(collector.beforeBatchMethods),
            immutableHookMap(collector.afterBatchMethods),
            immutableList(collector.invalidTests),
            immutableList(collector.invalidHooks),
            immutableList(collector.duplicateIds),
            immutableList(collector.issues));

        LOG.info(
            "Discovery complete: {} valid test(s), {} invalid test(s), {} invalid hook(s), {} duplicate id(s)"
                + " across {} class(es).",
            validTests.size(),
            collector.invalidTests.size(),
            collector.invalidHooks.size(),
            collector.duplicateIds.size(),
            holderAnnotationCount);
        return lastDiscoveryResult;
    }

    private static void processHolderClass(Class<?> clazz, DiscoveryCollector collector) {
        GameTestHolder holderAnn = clazz.getAnnotation(GameTestHolder.class);
        if (holderAnn == null) return;

        String namespace = holderAnn.value();
        String templatePrefix = holderAnn.templatePrefix();
        DiscoveryIssue namespaceIssue = validateNamespace(namespace, clazz);
        DiscoveryIssue templatePrefixIssue = validateTemplatePrefix(templatePrefix, clazz);

        for (Method method : clazz.getDeclaredMethods()) {
            GameTest testAnn = method.getAnnotation(GameTest.class);
            if (testAnn != null) {
                processTestMethod(
                    clazz,
                    method,
                    testAnn,
                    namespace,
                    templatePrefix,
                    namespaceIssue,
                    templatePrefixIssue,
                    collector);
            }

            BeforeBatch beforeAnn = method.getAnnotation(BeforeBatch.class);
            if (beforeAnn != null) {
                processBatchHook(method, HookPhase.BEFORE, beforeAnn.value(), namespaceIssue, collector);
            }

            AfterBatch afterAnn = method.getAnnotation(AfterBatch.class);
            if (afterAnn != null) {
                processBatchHook(method, HookPhase.AFTER, afterAnn.value(), namespaceIssue, collector);
            }
        }
    }

    private static void processTestMethod(Class<?> clazz, Method method, GameTest testAnn, String namespace,
        String templatePrefix, DiscoveryIssue namespaceIssue, DiscoveryIssue templatePrefixIssue,
        DiscoveryCollector collector) {

        List<DiscoveryIssue> issues = new ArrayList<>();
        if (namespaceIssue != null) issues.add(namespaceIssue);
        if (templatePrefixIssue != null) issues.add(templatePrefixIssue);
        collectTestMethodIssues(method, issues);
        collectBatchNameIssue(testAnn.batch(), method, "test", issues);
        if (testAnn.timeoutTicks() <= 0) {
            issues.add(
                issue(
                    "discovery:invalidTest:" + methodRef(method) + ":timeoutTicks",
                    KIND_DISCOVERY_ERROR,
                    "Skipping @GameTest method '" + method.getName()
                        + "' in '"
                        + clazz.getName()
                        + "': timeoutTicks must be greater than 0."));
        }
        if (testAnn.rotation() < 0 || testAnn.rotation() > 3) {
            issues.add(
                issue(
                    "discovery:invalidTest:" + methodRef(method) + ":rotation",
                    KIND_DISCOVERY_ERROR,
                    "Skipping @GameTest method '" + method.getName()
                        + "' in '"
                        + clazz.getName()
                        + "': rotation must be between 0 and 3."));
        }

        String intendedTestId = namespace + ":" + clazz.getSimpleName() + "." + method.getName();
        if (!issues.isEmpty()) {
            for (DiscoveryIssue issue : issues) {
                collector.issues.add(issue);
                LOG.warn(issue.message());
            }
            collector.invalidTests.add(new InvalidTestDefinition(intendedTestId, method, immutableList(issues)));
            return;
        }

        String resolvedTemplate = resolveTemplate(namespace, templatePrefix, testAnn.template(), method.getName());
        GameTestDefinition def = new GameTestDefinition(
            intendedTestId,
            method,
            resolvedTemplate,
            testAnn.timeoutTicks(),
            testAnn.batch(),
            testAnn.required(),
            testAnn.rotation());
        collector.validTests.add(def);
        LOG.debug("Registered test: {}", intendedTestId);
    }

    private static void processBatchHook(Method method, HookPhase phase, String batch, DiscoveryIssue namespaceIssue,
        DiscoveryCollector collector) {

        List<DiscoveryIssue> issues = new ArrayList<>();
        if (namespaceIssue != null) issues.add(namespaceIssue);
        collectBatchMethodIssues(method, phase, issues);
        collectBatchNameIssue(batch, method, phase == HookPhase.BEFORE ? "BeforeBatch" : "AfterBatch", issues);

        if (!issues.isEmpty()) {
            for (DiscoveryIssue issue : issues) {
                collector.issues.add(issue);
                LOG.warn(issue.message());
            }
            collector.invalidHooks.add(new InvalidBatchHook(phase, batch, method, immutableList(issues)));
            return;
        }

        Map<String, List<Method>> target = phase == HookPhase.BEFORE ? collector.beforeBatchMethods
            : collector.afterBatchMethods;
        target.computeIfAbsent(batch, k -> new ArrayList<>())
            .add(method);
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

    private static void collectTestMethodIssues(Method method, List<DiscoveryIssue> issues) {
        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
            issues.add(
                issue(
                    "discovery:invalidTest:" + methodRef(method) + ":modifiers",
                    KIND_DISCOVERY_ERROR,
                    "Skipping @GameTest method '" + method.getName()
                        + "' in '"
                        + method.getDeclaringClass()
                            .getName()
                        + "': must be public static."));
        }
        if (method.getReturnType() != Void.TYPE) {
            issues.add(
                issue(
                    "discovery:invalidTest:" + methodRef(method) + ":returnType",
                    KIND_DISCOVERY_ERROR,
                    "Skipping @GameTest method '" + method.getName()
                        + "' in '"
                        + method.getDeclaringClass()
                            .getName()
                        + "': must return void."));
        }
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 1 || params[0] != GameTestHelper.class) {
            issues.add(
                issue(
                    "discovery:invalidTest:" + methodRef(method) + ":parameters",
                    KIND_DISCOVERY_ERROR,
                    "Skipping @GameTest method '" + method.getName()
                        + "' in '"
                        + method.getDeclaringClass()
                            .getName()
                        + "': must take exactly one GameTestHelper parameter."));
        }
    }

    private static void collectBatchMethodIssues(Method method, HookPhase phase, List<DiscoveryIssue> issues) {
        int modifiers = method.getModifiers();
        String annotationName = phase == HookPhase.BEFORE ? "@BeforeBatch" : "@AfterBatch";
        if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
            issues.add(
                issue(
                    "discovery:invalidHook:" + phase.name()
                        .toLowerCase() + ":" + methodRef(method) + ":modifiers",
                    KIND_DISCOVERY_ERROR,
                    "Skipping " + annotationName
                        + " method '"
                        + method.getName()
                        + "' in '"
                        + method.getDeclaringClass()
                            .getName()
                        + "': must be public static."));
        }
        if (method.getParameterCount() != 0) {
            issues.add(
                issue(
                    "discovery:invalidHook:" + phase.name()
                        .toLowerCase() + ":" + methodRef(method) + ":parameters",
                    KIND_DISCOVERY_ERROR,
                    "Skipping " + annotationName
                        + " method '"
                        + method.getName()
                        + "' in '"
                        + method.getDeclaringClass()
                            .getName()
                        + "': must take no parameters."));
        }
    }

    private static DiscoveryIssue validateNamespace(String namespace, Class<?> holderClass) {
        if (namespace == null || namespace.isEmpty()) {
            return issue(
                "discovery:invalidHolder:" + holderClass.getName() + ":namespace",
                KIND_DISCOVERY_ERROR,
                "Invalid @GameTestHolder namespace in '" + holderClass.getName() + "': must not be empty.");
        }
        if (!namespace.matches("[a-z0-9_.-]+")) {
            return issue(
                "discovery:invalidHolder:" + holderClass.getName() + ":namespace",
                KIND_DISCOVERY_ERROR,
                "Invalid @GameTestHolder namespace '" + namespace
                    + "' in '"
                    + holderClass.getName()
                    + "': expected [a-z0-9_.-]+.");
        }
        return null;
    }

    private static DiscoveryIssue validateTemplatePrefix(String templatePrefix, Class<?> holderClass) {
        if (templatePrefix == null || templatePrefix.isEmpty()) {
            return null;
        }
        if (templatePrefix.startsWith("/") || templatePrefix.endsWith("/")
            || templatePrefix.contains("//")
            || templatePrefix.contains("..")) {
            return issue(
                "discovery:invalidHolder:" + holderClass.getName() + ":templatePrefix",
                KIND_DISCOVERY_ERROR,
                "Invalid @GameTestHolder templatePrefix '" + templatePrefix
                    + "' in '"
                    + holderClass.getName()
                    + "': must not start/end with '/', contain empty path segments, or contain '..'.");
        }
        return null;
    }

    private static void collectBatchNameIssue(String batch, Method method, String owner, List<DiscoveryIssue> issues) {
        if (batch == null) {
            issues.add(
                issue(
                    "discovery:invalidBatch:" + methodRef(method),
                    KIND_DISCOVERY_ERROR,
                    "Invalid " + owner + " batch on '" + methodRef(method) + "': batch must not be null."));
            return;
        }
        if (batch.isEmpty()) {
            return;
        }
        if (DEFAULT_BATCH_RENDER_NAME.equals(batch)) {
            issues.add(
                issue(
                    "discovery:invalidBatch:" + methodRef(method),
                    KIND_DISCOVERY_ERROR,
                    "Invalid " + owner
                        + " batch on '"
                        + methodRef(method)
                        + "': 'default' is reserved; use an empty string for the default batch."));
            return;
        }
        if (!batch.matches("[A-Za-z0-9_.-]+")) {
            issues.add(
                issue(
                    "discovery:invalidBatch:" + methodRef(method),
                    KIND_DISCOVERY_ERROR,
                    "Invalid " + owner
                        + " batch '"
                        + batch
                        + "' on '"
                        + methodRef(method)
                        + "': expected [A-Za-z0-9_.-]+."));
        }
    }

    private static Set<String> findDuplicates(DiscoveryCollector collector) {
        Map<String, List<GameTestDefinition>> byId = new LinkedHashMap<>();
        for (GameTestDefinition def : collector.validTests) {
            byId.computeIfAbsent(def.getTestId(), k -> new ArrayList<>())
                .add(def);
        }

        Set<String> duplicateIds = new HashSet<>();
        for (Map.Entry<String, List<GameTestDefinition>> entry : byId.entrySet()) {
            if (entry.getValue()
                .size() <= 1) {
                continue;
            }

            String testId = entry.getKey();
            duplicateIds.add(testId);
            List<Method> methods = new ArrayList<>();
            for (GameTestDefinition def : entry.getValue()) {
                methods.add(def.getMethod());
            }
            methods.sort(METHOD_ORDER);

            DiscoveryIssue issue = issue(
                "discovery:duplicateId:" + testId,
                KIND_DUPLICATE_TEST_ID,
                "Duplicate @GameTest id '" + testId
                    + "' found in "
                    + renderMethods(methods)
                    + "; all duplicates are excluded.");
            collector.duplicateIds.add(new DuplicateTestId(testId, immutableList(methods)));
            collector.issues.add(issue);
            LOG.warn(issue.message());
        }
        return duplicateIds;
    }

    private static void sortHookMap(Map<String, List<Method>> hooks) {
        for (List<Method> methods : hooks.values()) {
            methods.sort(METHOD_ORDER);
        }
    }

    private static void copyHookMap(Map<String, List<Method>> source, Map<String, List<Method>> target) {
        target.clear();
        for (Map.Entry<String, List<Method>> entry : source.entrySet()) {
            target.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    private static String renderMethods(List<Method> methods) {
        List<String> refs = new ArrayList<>();
        for (Method method : methods) {
            refs.add(methodRef(method));
        }
        return refs.toString();
    }

    private static String methodRef(Method method) {
        return method.getDeclaringClass()
            .getName() + "#"
            + method.getName();
    }

    private static DiscoveryIssue issue(String id, String kind, String message) {
        return new DiscoveryIssue(id, kind, message);
    }

    private static DiscoveryResult emptyResult() {
        return new DiscoveryResult(
            Collections.emptyList(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());
    }

    private static <T> List<T> immutableList(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static Map<String, List<Method>> immutableHookMap(Map<String, List<Method>> source) {
        Map<String, List<Method>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<Method>> entry : source.entrySet()) {
            copy.put(entry.getKey(), immutableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
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

    public static DiscoveryResult getLastDiscoveryResult() {
        return lastDiscoveryResult;
    }

    public static List<InvalidTestDefinition> getInvalidTests() {
        return lastDiscoveryResult.invalidTests();
    }

    public static List<InvalidBatchHook> getInvalidHooks() {
        return lastDiscoveryResult.invalidHooks();
    }

    public static List<DuplicateTestId> getDuplicateIds() {
        return lastDiscoveryResult.duplicateIds();
    }

    public static List<DiscoveryIssue> getDiscoveryIssues() {
        return lastDiscoveryResult.issues();
    }

    private static final class DiscoveryCollector {

        final List<GameTestDefinition> validTests = new ArrayList<>();
        final Map<String, List<Method>> beforeBatchMethods = new LinkedHashMap<>();
        final Map<String, List<Method>> afterBatchMethods = new LinkedHashMap<>();
        final List<InvalidTestDefinition> invalidTests = new ArrayList<>();
        final List<InvalidBatchHook> invalidHooks = new ArrayList<>();
        final List<DuplicateTestId> duplicateIds = new ArrayList<>();
        final List<DiscoveryIssue> issues = new ArrayList<>();
    }
}
