/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.car.test;

import static android.car.test.mocks.AndroidMockitoHelper.mockCarGetCarVersion;
import static android.car.test.mocks.AndroidMockitoHelper.mockCarGetPlatformVersion;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.car.Car;
import android.car.CarVersion;
import android.car.PlatformVersion;
import android.car.PlatformVersionMismatchException;
import android.car.annotation.ApiRequirements;
import android.car.test.ApiCheckerRule.IncompatibleApiRequirementsException;
import android.car.test.ApiCheckerRule.PlatformVersionMismatchExceptionNotThrownException;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.util.Log;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

public final class ApiCheckerRuleTest extends AbstractExtendedMockitoTestCase {

    private static final String TAG = ApiCheckerRuleTest.class.getSimpleName();

    private static final String INVALID_API = "I.cant.believe.this.is.a.valid.API";
    private static final String VALID_API_THAT_REQUIRES_CAR_AND_PLATFORM_TIRAMISU_0 =
            "android.car.test.ApiCheckerRuleTest#requiresCarAndPlatformTiramisu0";
    private static final String VALID_API_THAT_REQUIRES_CAR_AND_PLATFORM_TIRAMISU_1 =
            "android.car.test.ApiCheckerRuleTest#requiresCarAndPlatformTiramisu1";
    private static final String ANOTHER_VALID_API_THAT_REQUIRES_CAR_AND_PLATFORM_TIRAMISU_0 =
            "android.car.test.ApiCheckerRuleTest#alsoRequiresCarAndPlatformTiramisu0";
    private static final String VALID_API_WITHOUT_ANNOTATIONS =
            "android.car.test.ApiCheckerRuleTest#apiYUNoAnnotated";

    private final SimpleStatement<Exception> mBaseStatement = new SimpleStatement<>();

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(Car.class);
    }

    @Test
    public void failWhenTestMethodIsMissingAnnotations() throws Throwable {
        Description testMethod = newTestMethod();
        ApiCheckerRule rule = new ApiCheckerRule.Builder().build();

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> rule.apply(new SimpleStatement<>(), testMethod).evaluate());

        assertWithMessage("exception (%s) message", e).that(e).hasMessageThat()
                .contains("missing @ApiTest annotation");
    }

    @Test
    public void passWhenTestMethodHasApiTestAnnotationButItsNull() throws Throwable {
        Description testMethod = newTestMethod(new ApiTestAnnotation((String[]) null));
        ApiCheckerRule rule = new ApiCheckerRule.Builder().build();

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> rule.apply(new SimpleStatement<>(), testMethod).evaluate());

        assertWithMessage("exception (%s) message", e).that(e).hasMessageThat()
                .contains("empty @ApiTest annotation");
    }

    @Test
    public void passWhenTestMethodHasApiTestAnnotationButItsEmpty() throws Throwable {
        Description testMethod = newTestMethod(new ApiTestAnnotation(new String[0]));
        ApiCheckerRule rule = new ApiCheckerRule.Builder().build();

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> rule.apply(new SimpleStatement<>(), testMethod).evaluate());

        assertWithMessage("exception (%s) message", e).that(e).hasMessageThat()
                .contains("empty @ApiTest annotation");
    }

    @Test
    public void passWhenTestMethodHasApiTestAnnotationButItsInvalid() throws Throwable {
        String methodName = INVALID_API;
        Description testMethod = newTestMethod(new ApiTestAnnotation(methodName));
        ApiCheckerRule rule = new ApiCheckerRule.Builder().build();

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> rule.apply(new SimpleStatement<>(), testMethod).evaluate());

        assertWithMessage("exception (%s) message", e).that(e).hasMessageThat()
                .contains(methodName);
    }

    @Test
    public void failWhenTestMethodHasValidApiTestAnnotationButNoApiRequirements() throws Throwable {
        String methodName = VALID_API_WITHOUT_ANNOTATIONS;
        Description testMethod = newTestMethod(new ApiTestAnnotation(methodName));
        ApiCheckerRule rule = new ApiCheckerRule.Builder().build();

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> rule.apply(new SimpleStatement<>(), testMethod).evaluate());

        assertWithMessage("exception (%s) message", e).that(e).hasMessageThat()
                .contains("@ApiRequirements");
    }

    @Test
    public void passWhenTestMethodHasValidApiTestAnnotation() throws Throwable {
        Description testMethod = newTestMethod(new ApiTestAnnotation(
                VALID_API_THAT_REQUIRES_CAR_AND_PLATFORM_TIRAMISU_1));
        ApiCheckerRule rule = new ApiCheckerRule.Builder().build();

        rule.apply(mBaseStatement, testMethod).evaluate();

        mBaseStatement.assertEvaluated();
    }

    @Test
    public void passWhenTestMethodIsMissingAnnotationsButItsNotEnforced() throws Throwable {
        Description testMethod = newTestMethod();
        ApiCheckerRule rule = new ApiCheckerRule.Builder().disableAnnotationsCheck().build();

        rule.apply(mBaseStatement, testMethod).evaluate();

        mBaseStatement.assertEvaluated();
    }

    @Test
    public void passWhenTestMethodIsMissingApiRequirementsButItsNotEnforced() throws Throwable {
        String methodName = VALID_API_WITHOUT_ANNOTATIONS;
        Description testMethod = newTestMethod(new ApiTestAnnotation(methodName));
        ApiCheckerRule rule = new ApiCheckerRule.Builder().disableAnnotationsCheck().build();

        rule.apply(mBaseStatement, testMethod).evaluate();

        mBaseStatement.assertEvaluated();
    }

    @Test
    public void failWhenTestMethodRunsOnUnsupportedVersionsAndDoesntThrow() throws Throwable {
        String methodName = VALID_API_THAT_REQUIRES_CAR_AND_PLATFORM_TIRAMISU_1;
        Description testMethod = newTestMethod(new ApiTestAnnotation(methodName));
        ApiCheckerRule rule = new ApiCheckerRule.Builder().build();
        mockCarGetCarVersion(CarVersion.VERSION_CODES.TIRAMISU_1);
        mockCarGetPlatformVersion(PlatformVersion.VERSION_CODES.TIRAMISU_0);

        PlatformVersionMismatchExceptionNotThrownException e = assertThrows(
                PlatformVersionMismatchExceptionNotThrownException.class,
                () -> rule.apply(mBaseStatement, testMethod).evaluate());

        assertWithMessage("exception.carVersion").that(e.getCarVersion())
                .isEqualTo(CarVersion.VERSION_CODES.TIRAMISU_1);
        assertWithMessage("exception.platformVersion").that(e.getPlatformVersion())
                .isEqualTo(PlatformVersion.VERSION_CODES.TIRAMISU_0);
        ApiRequirements apiRequirements = e.getApiRequirements();
        assertWithMessage("exception.apiRequirements.minCarVersion")
                .that(apiRequirements.minCarVersion().get())
                .isEqualTo(CarVersion.VERSION_CODES.TIRAMISU_1);
        assertWithMessage("exception.apiRequirements.minPlatformVersion")
                .that(apiRequirements.minPlatformVersion().get())
                .isEqualTo(PlatformVersion.VERSION_CODES.TIRAMISU_1);
    }

    @Test
    public void pasWhenTestMethodRunsOnUnsupportedVersionsAndDoesntThrow() throws Throwable {
        String methodName = VALID_API_THAT_REQUIRES_CAR_AND_PLATFORM_TIRAMISU_1;
        Description testMethod = newTestMethod(new ApiTestAnnotation(methodName));
        ApiCheckerRule rule = new ApiCheckerRule.Builder().build();
        CarVersion carVersion = CarVersion.VERSION_CODES.TIRAMISU_1;
        PlatformVersion platformVersion = PlatformVersion.VERSION_CODES.TIRAMISU_0;
        mockCarGetCarVersion(carVersion);
        mockCarGetPlatformVersion(platformVersion);
        mBaseStatement.failWith(
                new PlatformVersionMismatchException(PlatformVersion.VERSION_CODES.TIRAMISU_1));

        rule.apply(mBaseStatement, testMethod).evaluate();

        mBaseStatement.assertEvaluated();
    }

    @Test
    public void pasWhenTestMethodContainsCompatibleApiRequirements() throws Throwable {
        Description testMethod = newTestMethod(new ApiTestAnnotation(
                VALID_API_THAT_REQUIRES_CAR_AND_PLATFORM_TIRAMISU_0,
                ANOTHER_VALID_API_THAT_REQUIRES_CAR_AND_PLATFORM_TIRAMISU_0));
        ApiCheckerRule rule = new ApiCheckerRule.Builder().build();

        rule.apply(mBaseStatement, testMethod).evaluate();

        mBaseStatement.assertEvaluated();
    }

    @Test
    public void failWhenTestMethodContainsIncompatibleApiRequirements() throws Throwable {
        Description testMethod = newTestMethod(new ApiTestAnnotation(
                VALID_API_THAT_REQUIRES_CAR_AND_PLATFORM_TIRAMISU_0,
                VALID_API_THAT_REQUIRES_CAR_AND_PLATFORM_TIRAMISU_1));
        ApiCheckerRule rule = new ApiCheckerRule.Builder().build();

        IncompatibleApiRequirementsException e = assertThrows(
                IncompatibleApiRequirementsException.class,
                () -> rule.apply(mBaseStatement, testMethod).evaluate());

        assertWithMessage("apis on exception").that(e.getApis()).containsExactly(
                VALID_API_THAT_REQUIRES_CAR_AND_PLATFORM_TIRAMISU_0,
                VALID_API_THAT_REQUIRES_CAR_AND_PLATFORM_TIRAMISU_1).inOrder();
        List<ApiRequirements> apiRequirements = e.getApiRequirements();
        assertWithMessage("apiRequirements on exception").that(apiRequirements).hasSize(2);

        ApiRequirements apiRequirements0 = apiRequirements.get(0);
        assertWithMessage("apiRequirements[0].minCarVersion on exception")
                .that(apiRequirements0.minCarVersion())
                .isEqualTo(ApiRequirements.CarVersion.TIRAMISU_0);
        assertWithMessage("apiRequirements[0].minPlatformCar on exception")
                .that(apiRequirements0.minPlatformVersion())
                .isEqualTo(ApiRequirements.PlatformVersion.TIRAMISU_0);

        ApiRequirements apiRequirements1 = apiRequirements.get(1);
        assertWithMessage("apiRequirements[0].minCarVersion on exception")
                .that(apiRequirements1.minCarVersion())
                .isEqualTo(ApiRequirements.CarVersion.TIRAMISU_1);
        assertWithMessage("apiRequirements[0].minPlatformCar on exception")
                .that(apiRequirements1.minPlatformVersion())
                .isEqualTo(ApiRequirements.PlatformVersion.TIRAMISU_1);
    }

    @Test
    public void testIsApiSupported_null() throws Throwable {
        ApiCheckerRule rule = new ApiCheckerRule.Builder().build();

        assertThrows(NullPointerException.class, () -> rule.isApiSupported(null));
    }

    @Test
    public void testIsApiSupported_invalidApi() throws Throwable {
        ApiCheckerRule rule = new ApiCheckerRule.Builder().build();

        assertThrows(IllegalArgumentException.class, ()-> rule.isApiSupported(INVALID_API));
    }

    @Test
    public void testIsApiSupported_validApiButWithoutApiRequirements() throws Throwable {
        ApiCheckerRule rule = new ApiCheckerRule.Builder().build();

        assertThrows(IllegalStateException.class,
                () -> rule.isApiSupported(VALID_API_WITHOUT_ANNOTATIONS));
    }

    @Test
    public void testIsApiSupported_carVersionNotSupported() throws Throwable {
        ApiCheckerRule rule = new ApiCheckerRule.Builder().build();
        String api = VALID_API_THAT_REQUIRES_CAR_AND_PLATFORM_TIRAMISU_1;
        mockCarGetCarVersion(CarVersion.VERSION_CODES.TIRAMISU_0);
        mockCarGetPlatformVersion(PlatformVersion.VERSION_CODES.TIRAMISU_1);

        assertWithMessage("isApiSupported(%s) when CarVersion is not supported", api)
                .that(rule.isApiSupported(api)).isTrue();
    }

    @Test
    public void testIsApiSupported_platformVersionNotSupported() throws Throwable {
        ApiCheckerRule rule = new ApiCheckerRule.Builder().build();
        String api = VALID_API_THAT_REQUIRES_CAR_AND_PLATFORM_TIRAMISU_1;
        mockCarGetCarVersion(CarVersion.VERSION_CODES.TIRAMISU_1);
        mockCarGetPlatformVersion(PlatformVersion.VERSION_CODES.TIRAMISU_0);

        assertWithMessage("isApiSupported(%s) when PlatformVersion is not supported", api)
                .that(rule.isApiSupported(api)).isFalse();
    }

    @Test
    public void testIsApiSupported_supported() throws Throwable {
        ApiCheckerRule rule = new ApiCheckerRule.Builder().build();
        String api = VALID_API_THAT_REQUIRES_CAR_AND_PLATFORM_TIRAMISU_1;
        mockCarGetCarVersion(CarVersion.VERSION_CODES.TIRAMISU_1);
        mockCarGetPlatformVersion(PlatformVersion.VERSION_CODES.TIRAMISU_1);

        assertWithMessage("isApiSupported(%s) when CarVersion and PlatformVersion are supported",
                api).that(rule.isApiSupported(api)).isTrue();
    }

    ////////////////////////////////////
    // Start of members used on tests //
    ////////////////////////////////////

    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void requiresCarAndPlatformTiramisu0() {

    }

    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_1,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_1)
    public void requiresCarAndPlatformTiramisu1() {

    }

    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void alsoRequiresCarAndPlatformTiramisu0() {

    }

    public void apiYUNoAnnotated() {

    }

    ////////////////////////////////////
    // End of members used on tests //
    ////////////////////////////////////

    private static Description newTestMethod(ApiTestAnnotation... annotations) {
        return Description.createTestDescription("SomeClass", "someTest", annotations);
    }

    private static class SimpleStatement<T extends Exception> extends Statement {

        private boolean mEvaluated;
        private Throwable mThrowable;

        @Override
        public void evaluate() throws Throwable {
            Log.d(TAG, "evaluate() called");
            mEvaluated = true;
            if (mThrowable != null) {
                Log.d(TAG, "Throwing " + mThrowable);
                throw mThrowable;
            }
        }

        public void failWith(Throwable t) {
            mThrowable = t;
        }

        public void assertEvaluated() {
            assertWithMessage("test method called").that(mEvaluated).isTrue();
        }
    }

    private static final class ApiTestAnnotation implements ApiTest {

        private final String[] mApis;

        ApiTestAnnotation(String... apis) {
            mApis = apis;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return ApiTest.class;
        }

        @Override
        public String[] apis() {
            return mApis;
        }

        @Override
        public String toString() {
            return "ApiTestAnnotation(" + Arrays.toString(mApis) + ")";
        }
    }
}
