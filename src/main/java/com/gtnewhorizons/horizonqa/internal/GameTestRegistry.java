package com.gtnewhorizons.horizonqa.internal;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.AfterBatch;
import com.gtnewhorizons.horizonqa.api.annotation.BeforeBatch;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.annotation.MethodSource;
import com.gtnewhorizons.horizonqa.internal.InvalidBatchHook.HookPhase;
import com.gtnewhorizons.horizonqa.internal.MethodSourceResolver.MethodSourceException;
import com.gtnewhorizons.horizonqa.internal.MethodSourceResolver.ResolvedArguments;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.discovery.ASMDataTable;

public final class GameTestRegistry {

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private static final String KIND_DISCOVERY_ERROR = "DISCOVERY_ERROR";
    private static final String KIND_DUPLICATE_TEST_ID = "DUPLICATE_TEST_ID";

    private static final String DEFAULT_BATCH_RENDER_NAME = "default";

    private static final Comparator<Method> METHOD_ORDER = Comparator.comparing(
        (Method m) -> m.getDeclaringClass()
            .getName())
        .thenComparing(Method::getName)
        .thenComparing(GameTestRegistry::erasedMethodDescriptor);

    private GameTestRegistry() {}

    public static GameTestCatalog discoverTests(ASMDataTable asmData) {
        return discoverTests(asmData, Loader::isModLoaded);
    }

    static GameTestCatalog discoverTests(ASMDataTable asmData, Predicate<String> modLoaded) {
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

        Set<ASMDataTable.ASMData> testAnnotations = asmData.getAll(GameTest.class.getName());
        Set<ASMDataTable.ASMData> methodSourceAnnotations = asmData.getAll(MethodSource.class.getName());
        List<PendingHolder> pendingHolders = new ArrayList<>();
        for (ASMDataTable.ASMData holderData : holderAnnotations) {
            String className = holderData.getClassName();
            List<String> missingMods = missingRequiredMods(holderData.getAnnotationInfo(), modLoaded);
            if (!missingMods.isEmpty()) {
                registerMissingModOrigins(holderData, testAnnotations, methodSourceAnnotations, collector);
                pendingHolders.add(PendingHolder.missingMods(holderData, missingMods));
                continue;
            }
            try {
                Class<?> holderClass = Class.forName(className, false, GameTestRegistry.class.getClassLoader());
                registerHolderOrigins(holderClass, collector);
                pendingHolders.add(PendingHolder.loaded(holderClass));
            } catch (ClassNotFoundException | LinkageError e) {
                DiscoveryIssue issue = issue(
                    "discovery:holderLoad:" + className,
                    KIND_DISCOVERY_ERROR,
                    "Could not load @GameTestHolder class '" + className + "': " + e.getMessage());
                collector.issues.add(issue);
                LOG.error("Could not load @GameTestHolder class '{}'", className, e);
            }
        }

        Set<String> duplicateIds = findDuplicates(collector);
        for (PendingHolder pending : pendingHolders) {
            if (pending.holderClass != null) {
                processHolderClass(pending.holderClass, duplicateIds, collector);
            } else {
                processMissingModHolder(
                    pending.holderData,
                    testAnnotations,
                    methodSourceAnnotations,
                    pending.missingMods,
                    collector);
            }
        }
        return publish(collector, duplicateIds, holderAnnotations.size());
    }

    private static List<String> missingRequiredMods(Map<String, Object> annotationInfo, Predicate<String> modLoaded) {
        List<String> requiredMods = annotationStrings(annotationInfo, "requiredMods");
        List<String> missing = new ArrayList<>();
        for (String modId : new LinkedHashSet<>(requiredMods)) {
            if (!modId.isEmpty() && !modLoaded.test(modId)) {
                missing.add(modId);
            }
        }
        return missing;
    }

    private static void registerMissingModOrigins(ASMDataTable.ASMData holderData,
        Set<ASMDataTable.ASMData> testAnnotations, Set<ASMDataTable.ASMData> methodSourceAnnotations,
        DiscoveryCollector collector) {
        Map<String, Object> holderInfo = holderData.getAnnotationInfo();
        String className = holderData.getClassName();
        String namespace = annotationString(holderInfo, "value", "");
        String templatePrefix = annotationString(holderInfo, "templatePrefix", "");
        if (!validNamespace(namespace) || !validTemplatePrefix(templatePrefix) || testAnnotations == null) {
            return;
        }
        for (ASMDataTable.ASMData testData : testAnnotations) {
            if (!className.equals(testData.getClassName())) {
                continue;
            }
            String methodName = methodName(testData.getObjectName());
            String testId = namespace + ":" + simpleClassName(className) + "." + methodName;
            boolean parameterized = hasMethodAnnotation(methodSourceAnnotations, className, testData.getObjectName());
            collector.addTestOrigin(
                TestOrigin.unloaded(testId, className, className + "#" + testData.getObjectName(), parameterized));
        }
    }

    private static void registerHolderOrigins(Class<?> holderClass, DiscoveryCollector collector) {
        GameTestHolder holderAnn = holderClass.getAnnotation(GameTestHolder.class);
        if (holderAnn == null) {
            return;
        }
        for (Method method : holderClass.getDeclaredMethods()) {
            if (method.getAnnotation(GameTest.class) == null) {
                continue;
            }
            collector.addTestOrigin(
                TestOrigin.loaded(
                    intendedTestId(holderAnn.value(), holderClass, method),
                    method,
                    method.getAnnotation(MethodSource.class) != null));
        }
    }

    private static void processMissingModHolder(ASMDataTable.ASMData holderData,
        Set<ASMDataTable.ASMData> testAnnotations, Set<ASMDataTable.ASMData> methodSourceAnnotations,
        List<String> missingMods, DiscoveryCollector collector) {
        Map<String, Object> holderInfo = holderData.getAnnotationInfo();
        String className = holderData.getClassName();
        String namespace = annotationString(holderInfo, "value", "");
        String templatePrefix = annotationString(holderInfo, "templatePrefix", "");
        String skipReason = missingMods.size() == 1 ? "Required mod is not loaded: " + missingMods.get(0)
            : "Required mods are not loaded: " + String.join(", ", missingMods);

        if (!validNamespace(namespace) || !validTemplatePrefix(templatePrefix)) {
            DiscoveryIssue issue = issue(
                "discovery:invalidGatedHolder:" + className,
                KIND_DISCOVERY_ERROR,
                "Cannot register mod-gated @GameTestHolder '" + className
                    + "': its namespace or templatePrefix is invalid.");
            collector.issues.add(issue);
            LOG.warn(issue.message());
            return;
        }

        int skippedCount = 0;
        for (ASMDataTable.ASMData testData : testAnnotations) {
            if (!className.equals(testData.getClassName())) {
                continue;
            }
            String methodName = methodName(testData.getObjectName());
            Map<String, Object> testInfo = testData.getAnnotationInfo();
            String rawTemplate = annotationString(testInfo, "template", "");
            int timeoutTicks = annotationInt(testInfo, "timeoutTicks", 100);
            String batch = annotationString(testInfo, "batch", "");
            boolean required = annotationBoolean(testInfo, "required", true);
            int rotation = annotationInt(testInfo, "rotation", 0);
            String testId = namespace + ":" + simpleClassName(className) + "." + methodName;
            boolean parameterized = hasMethodAnnotation(methodSourceAnnotations, className, testData.getObjectName());
            collector.validTests.add(
                parameterized
                    ? GameTestDefinition.parameterizedSkippedAtDiscovery(
                        testId,
                        className,
                        resolveTemplate(namespace, templatePrefix, rawTemplate, methodName),
                        timeoutTicks,
                        batch,
                        required,
                        rotation,
                        skipReason)
                    : GameTestDefinition.skippedAtDiscovery(
                        testId,
                        className,
                        resolveTemplate(namespace, templatePrefix, rawTemplate, methodName),
                        timeoutTicks,
                        batch,
                        required,
                        rotation,
                        skipReason));
            skippedCount++;
        }
        LOG.info(
            "Skipped loading @GameTestHolder '{}' because {} ({} test(s) gated).",
            className,
            skipReason,
            skippedCount);
    }

    private static List<String> annotationStrings(Map<String, Object> annotationInfo, String name) {
        if (annotationInfo == null) {
            return Collections.emptyList();
        }
        Object value = annotationInfo.get(name);
        if (value instanceof List<?>values) {
            List<String> strings = new ArrayList<>();
            for (Object item : values) {
                if (item instanceof String string) {
                    strings.add(string);
                }
            }
            return strings;
        }
        if (value instanceof String string) {
            return Collections.singletonList(string);
        }
        return Collections.emptyList();
    }

    private static String annotationString(Map<String, Object> annotationInfo, String name, String fallback) {
        if (annotationInfo == null) {
            return fallback;
        }
        Object value = annotationInfo.get(name);
        return value instanceof String string ? string : fallback;
    }

    private static int annotationInt(Map<String, Object> annotationInfo, String name, int fallback) {
        if (annotationInfo == null) {
            return fallback;
        }
        Object value = annotationInfo.get(name);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static boolean annotationBoolean(Map<String, Object> annotationInfo, String name, boolean fallback) {
        if (annotationInfo == null) {
            return fallback;
        }
        Object value = annotationInfo.get(name);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static String methodName(String objectName) {
        int descriptor = objectName == null ? -1 : objectName.indexOf('(');
        return descriptor >= 0 ? objectName.substring(0, descriptor) : objectName;
    }

    private static boolean hasMethodAnnotation(Set<ASMDataTable.ASMData> annotations, String className,
        String objectName) {
        if (annotations == null || annotations.isEmpty()) {
            return false;
        }
        for (ASMDataTable.ASMData annotation : annotations) {
            boolean sameObject = objectName == null ? annotation.getObjectName() == null
                : objectName.equals(annotation.getObjectName());
            if (className.equals(annotation.getClassName()) && sameObject) {
                return true;
            }
        }
        return false;
    }

    private static String simpleClassName(String className) {
        int nested = className.lastIndexOf('$');
        int separator = nested >= 0 ? nested : className.lastIndexOf('.');
        return separator >= 0 ? className.substring(separator + 1) : className;
    }

    private static GameTestCatalog publish(DiscoveryCollector collector, Set<String> duplicateIds,
        int holderAnnotationCount) {

        List<GameTestDefinition> validTests = new ArrayList<>();
        for (GameTestDefinition def : collector.validTests) {
            if (!duplicateIds.contains(def.getBaseTestId())) {
                validTests.add(def);
            }
        }
        validTests.sort(GameTestDefinition.executionOrder());

        sortHookMap(collector.beforeBatchMethods);
        sortHookMap(collector.afterBatchMethods);

        List<InvalidTestDefinition> invalidTests = sortedInvalidTests(collector.invalidTests);
        List<InvalidBatchHook> invalidHooks = sortedInvalidHooks(collector.invalidHooks);
        List<DuplicateTestId> duplicateIdsList = sortedDuplicateIds(collector.duplicateIds);
        List<DiscoveryIssue> issues = sortedIssues(collector.issues);

        GameTestCatalog catalog = new GameTestCatalog(
            validTests,
            collector.beforeBatchMethods,
            collector.afterBatchMethods,
            invalidTests,
            invalidHooks,
            duplicateIdsList,
            issues);

        LOG.info(
            "Discovery complete: {} valid test(s), {} invalid test(s), {} invalid hook(s), {} duplicate id(s)"
                + " across {} class(es).",
            validTests.size(),
            collector.invalidTests.size(),
            collector.invalidHooks.size(),
            collector.duplicateIds.size(),
            holderAnnotationCount);
        return catalog;
    }

    private static void processHolderClass(Class<?> clazz, Set<String> duplicateIds, DiscoveryCollector collector) {
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
                    duplicateIds,
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
        Set<String> duplicateIds, DiscoveryCollector collector) {

        String intendedTestId = intendedTestId(namespace, clazz, method);
        if (duplicateIds.contains(intendedTestId)) {
            return;
        }

        List<DiscoveryIssue> issues = new ArrayList<>();
        if (namespaceIssue != null) issues.add(namespaceIssue);
        if (templatePrefixIssue != null && usesTemplatePrefix(testAnn.template(), templatePrefix)) {
            issues.add(templatePrefixIssue);
        }
        MethodSource methodSource = method.getAnnotation(MethodSource.class);
        collectTestMethodIssues(method, methodSource != null, issues);
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

        if (!issues.isEmpty()) {
            for (DiscoveryIssue issue : issues) {
                collector.issues.add(issue);
                LOG.warn(issue.message());
            }
            collector.invalidTests.add(new InvalidTestDefinition(intendedTestId, method, issues));
            return;
        }

        String resolvedTemplate = resolveTemplate(namespace, templatePrefix, testAnn.template(), method.getName());
        GameTestDefinition baseDefinition = new GameTestDefinition(
            intendedTestId,
            method,
            resolvedTemplate,
            testAnn.timeoutTicks(),
            testAnn.batch(),
            testAnn.required(),
            testAnn.rotation());
        if (methodSource == null) {
            collector.validTests.add(baseDefinition);
            LOG.debug("Registered test: {}", intendedTestId);
            return;
        }

        List<ResolvedArguments> invocations;
        try {
            invocations = MethodSourceResolver.resolve(method, methodSource);
        } catch (MethodSourceException e) {
            rejectMethodSource(intendedTestId, method, e.getMessage(), e, collector);
            return;
        }

        List<GameTestDefinition> expanded = new ArrayList<>(invocations.size());
        try {
            for (ResolvedArguments invocation : invocations) {
                expanded
                    .add(baseDefinition.withArguments(invocation.name(), invocation.ordinal(), invocation.arguments()));
            }
        } catch (RuntimeException | LinkageError e) {
            String detail = e.getClass()
                .getName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            rejectMethodSource(
                intendedTestId,
                method,
                "failed while materializing supplied arguments: " + detail,
                e,
                collector);
            return;
        }
        collector.validTests.addAll(expanded);
        for (GameTestDefinition def : expanded) {
            LOG.debug("Registered test: {}", def.getTestId());
        }
    }

    private static void rejectMethodSource(String intendedTestId, Method method, String reason, Throwable failure,
        DiscoveryCollector collector) {
        DiscoveryIssue issue = issue(
            "discovery:invalidTest:" + methodRef(method) + ":methodSource",
            KIND_DISCOVERY_ERROR,
            "Skipping @GameTest method '" + method.getName()
                + "' in '"
                + method.getDeclaringClass()
                    .getName()
                + "': "
                + reason
                + ".");
        collector.issues.add(issue);
        collector.invalidTests.add(new InvalidTestDefinition(intendedTestId, method, Collections.singletonList(issue)));
        LOG.warn(issue.message(), failure);
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
            collector.invalidHooks.add(new InvalidBatchHook(phase, batch, method, issues));
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

    private static boolean usesTemplatePrefix(String rawTemplate, String templatePrefix) {
        return templatePrefix != null && !templatePrefix.isEmpty()
            && !rawTemplate.isEmpty()
            && !rawTemplate.contains(":");
    }

    private static String intendedTestId(String namespace, Class<?> clazz, Method method) {
        String renderedNamespace = namespace == null || namespace.isEmpty() ? "invalid-namespace" : namespace;
        return renderedNamespace + ":" + clazz.getSimpleName() + "." + method.getName();
    }

    private static void collectTestMethodIssues(Method method, boolean parameterized, List<DiscoveryIssue> issues) {
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
        boolean validParameters = parameterized ? params.length >= 2 && params[0] == GameTestHelper.class
            : params.length == 1 && params[0] == GameTestHelper.class;
        if (!validParameters) {
            String expected = parameterized ? "must take GameTestHelper followed by one or more supplied parameters"
                : "must take exactly one GameTestHelper parameter";
            issues.add(
                issue(
                    "discovery:invalidTest:" + methodRef(method) + ":parameters",
                    KIND_DISCOVERY_ERROR,
                    "Skipping @GameTest method '" + method.getName()
                        + "' in '"
                        + method.getDeclaringClass()
                            .getName()
                        + "': "
                        + expected
                        + "."));
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
        if (method.getReturnType() != Void.TYPE) {
            issues.add(
                issue(
                    "discovery:invalidHook:" + phase.name()
                        .toLowerCase() + ":" + methodRef(method) + ":returnType",
                    KIND_DISCOVERY_ERROR,
                    "Skipping " + annotationName
                        + " method '"
                        + method.getName()
                        + "' in '"
                        + method.getDeclaringClass()
                            .getName()
                        + "': must return void."));
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
        if (!validNamespace(namespace)) {
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

    private static boolean validNamespace(String namespace) {
        return namespace != null && namespace.matches("[a-z0-9_.-]+");
    }

    private static DiscoveryIssue validateTemplatePrefix(String templatePrefix, Class<?> holderClass) {
        if (templatePrefix == null || templatePrefix.isEmpty()) {
            return null;
        }
        if (!validTemplatePrefix(templatePrefix)) {
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

    private static boolean validTemplatePrefix(String templatePrefix) {
        return templatePrefix != null && !templatePrefix.startsWith("/")
            && !templatePrefix.endsWith("/")
            && !templatePrefix.contains("//")
            && !templatePrefix.contains("..");
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
        Set<String> duplicateIds = new HashSet<>();
        for (Map.Entry<String, List<TestOrigin>> entry : collector.testOriginsById.entrySet()) {
            if (entry.getValue()
                .size() <= 1) {
                continue;
            }

            String testId = entry.getKey();
            duplicateIds.add(testId);
            List<Method> methods = new ArrayList<>();
            List<String> sourceRefs = new ArrayList<>();
            Set<String> holderClassNames = new LinkedHashSet<>();
            boolean parameterized = false;
            for (TestOrigin origin : entry.getValue()) {
                if (!origin.holderClassName.isEmpty()) {
                    holderClassNames.add(origin.holderClassName);
                }
                if (origin.method != null && !methods.contains(origin.method)) {
                    methods.add(origin.method);
                }
                sourceRefs.add(origin.sourceRef);
                parameterized |= origin.parameterized;
            }
            methods.sort(METHOD_ORDER);
            Collections.sort(sourceRefs);

            DiscoveryIssue issue = issue(
                "discovery:duplicateId:" + testId,
                KIND_DUPLICATE_TEST_ID,
                "Duplicate @GameTest id '" + testId + "' found in " + sourceRefs + "; all duplicates are excluded.");
            collector.duplicateIds
                .add(new DuplicateTestId(testId, methods, new ArrayList<>(holderClassNames), parameterized));
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

    private static List<InvalidTestDefinition> sortedInvalidTests(List<InvalidTestDefinition> invalidTests) {
        List<InvalidTestDefinition> sorted = new ArrayList<>(invalidTests);
        sorted.sort(
            Comparator.comparing(InvalidTestDefinition::intendedTestId)
                .thenComparing(invalidTest -> methodRef(invalidTest.method())));
        return sorted;
    }

    private static List<InvalidBatchHook> sortedInvalidHooks(List<InvalidBatchHook> invalidHooks) {
        List<InvalidBatchHook> sorted = new ArrayList<>(invalidHooks);
        sorted.sort(
            Comparator.comparing(
                (InvalidBatchHook invalidHook) -> invalidHook.phase()
                    .name())
                .thenComparing(invalidHook -> invalidHook.batch() == null ? "" : invalidHook.batch())
                .thenComparing(invalidHook -> methodRef(invalidHook.method())));
        return sorted;
    }

    private static List<DuplicateTestId> sortedDuplicateIds(List<DuplicateTestId> duplicateIds) {
        List<DuplicateTestId> sorted = new ArrayList<>(duplicateIds);
        sorted.sort(Comparator.comparing(DuplicateTestId::testId));
        return sorted;
    }

    private static List<DiscoveryIssue> sortedIssues(List<DiscoveryIssue> issues) {
        List<DiscoveryIssue> sorted = new ArrayList<>(issues);
        sorted.sort(
            Comparator.comparing(DiscoveryIssue::id)
                .thenComparing(DiscoveryIssue::kind)
                .thenComparing(DiscoveryIssue::message));
        return sorted;
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

    private static String methodSignatureRef(Method method) {
        return appendErasedParameters(new StringBuilder(methodRef(method)), method).toString();
    }

    private static String erasedMethodDescriptor(Method method) {
        return appendErasedParameters(new StringBuilder(), method).append(':')
            .append(erasedTypeName(method.getReturnType()))
            .toString();
    }

    private static StringBuilder appendErasedParameters(StringBuilder ref, Method method) {
        ref.append('(');
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) ref.append(',');
            ref.append(erasedTypeName(parameterTypes[i]));
        }
        return ref.append(')');
    }

    private static String erasedTypeName(Class<?> type) {
        if (type.isArray()) {
            return erasedTypeName(type.getComponentType()) + "[]";
        }
        return type.getName();
    }

    private static DiscoveryIssue issue(String id, String kind, String message) {
        return new DiscoveryIssue(id, kind, message);
    }

    private static <T> List<T> immutableList(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    @Desugar
    private record PendingHolder(ASMDataTable.ASMData holderData, Class<?> holderClass, List<String> missingMods) {

        static PendingHolder loaded(Class<?> holderClass) {
            return new PendingHolder(null, holderClass, Collections.emptyList());
        }

        static PendingHolder missingMods(ASMDataTable.ASMData holderData, List<String> missingMods) {
            return new PendingHolder(holderData, null, immutableList(missingMods));
        }
    }

    @Desugar
    private record TestOrigin(String testId, Method method, String holderClassName, String sourceRef,
        boolean parameterized) {

        static TestOrigin loaded(String testId, Method method, boolean parameterized) {
            return new TestOrigin(
                testId,
                method,
                method.getDeclaringClass()
                    .getName(),
                methodSignatureRef(method),
                parameterized);
        }

        static TestOrigin unloaded(String testId, String holderClassName, String sourceRef, boolean parameterized) {
            return new TestOrigin(testId, null, holderClassName, sourceRef, parameterized);
        }
    }

    private static final class DiscoveryCollector {

        final Map<String, List<TestOrigin>> testOriginsById = new LinkedHashMap<>();
        final List<GameTestDefinition> validTests = new ArrayList<>();
        final Map<String, List<Method>> beforeBatchMethods = new LinkedHashMap<>();
        final Map<String, List<Method>> afterBatchMethods = new LinkedHashMap<>();
        final List<InvalidTestDefinition> invalidTests = new ArrayList<>();
        final List<InvalidBatchHook> invalidHooks = new ArrayList<>();
        final List<DuplicateTestId> duplicateIds = new ArrayList<>();
        final List<DiscoveryIssue> issues = new ArrayList<>();

        void addTestOrigin(TestOrigin origin) {
            testOriginsById.computeIfAbsent(origin.testId, ignored -> new ArrayList<>())
                .add(origin);
        }
    }
}
