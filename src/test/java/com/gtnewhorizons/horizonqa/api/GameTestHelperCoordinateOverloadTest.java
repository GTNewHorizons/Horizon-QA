package com.gtnewhorizons.horizonqa.api;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.junit.Test;

public class GameTestHelperCoordinateOverloadTest {

    @Test
    public void everyTestPosOverloadHasEquivalentLabelAndCoordinateOverloads() {
        Method[] methods = GameTestHelper.class.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::toGenericString));
        List<String> missing = new ArrayList<>();

        for (Method method : methods) {
            if (!isPublicApiMethod(method) || !hasTestPosParameter(method)) {
                continue;
            }
            if (!hasLabelEquivalent(method)) {
                missing.add(method.toGenericString() + " has no equivalent String-label overload");
            }
            if (!hasCoordinateEquivalent(method, methods)) {
                missing.add(method.toGenericString() + " has no equivalent x/y/z overload");
            }
        }

        assertTrue(String.join("\n", missing), missing.isEmpty());
    }

    private static boolean isPublicApiMethod(Method method) {
        return Modifier.isPublic(method.getModifiers()) && !method.isSynthetic();
    }

    private static boolean hasTestPosParameter(Method method) {
        for (Class<?> parameterType : method.getParameterTypes()) {
            if (parameterType == TestPos.class) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLabelEquivalent(Method testPosMethod) {
        Class<?>[] labelParameters = testPosMethod.getParameterTypes()
            .clone();
        for (int i = 0; i < labelParameters.length; i++) {
            if (labelParameters[i] == TestPos.class) {
                labelParameters[i] = String.class;
            }
        }
        try {
            Method labelMethod = GameTestHelper.class.getDeclaredMethod(testPosMethod.getName(), labelParameters);
            return isPublicApiMethod(labelMethod) && labelMethod.getReturnType() == testPosMethod.getReturnType();
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private static boolean hasCoordinateEquivalent(Method testPosMethod, Method[] methods) {
        for (Method candidate : methods) {
            if (isPublicApiMethod(candidate) && candidate.getName()
                .equals(testPosMethod.getName())
                && candidate.getReturnType() == testPosMethod.getReturnType()
                && coordinatesReplaceTestPositions(testPosMethod.getParameterTypes(), candidate.getParameterTypes())) {
                return true;
            }
        }
        return false;
    }

    private static boolean coordinatesReplaceTestPositions(Class<?>[] testPosParameters, Class<?>[] candidate) {
        int candidateIndex = 0;
        for (Class<?> parameter : testPosParameters) {
            if (parameter == TestPos.class) {
                if (!hasCoordinateTriple(candidate, candidateIndex)) {
                    return false;
                }
                candidateIndex += 3;
            } else if (candidateIndex >= candidate.length || candidate[candidateIndex++] != parameter) {
                return false;
            }
        }
        return candidateIndex == candidate.length;
    }

    private static boolean hasCoordinateTriple(Class<?>[] parameters, int offset) {
        if (offset + 2 >= parameters.length) {
            return false;
        }
        Class<?> coordinateType = parameters[offset];
        return (coordinateType == int.class || coordinateType == double.class)
            && parameters[offset + 1] == coordinateType
            && parameters[offset + 2] == coordinateType;
    }
}
