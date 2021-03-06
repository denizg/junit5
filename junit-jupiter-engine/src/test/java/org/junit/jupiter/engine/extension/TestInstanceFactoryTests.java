/*
 * Copyright 2015-2018 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.jupiter.engine.extension;

import static org.assertj.core.api.Assertions.allOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.platform.commons.util.ClassUtils.nullSafeToString;
import static org.junit.platform.testkit.ExecutionEventConditions.assertRecordedExecutionEventsContainsExactly;
import static org.junit.platform.testkit.ExecutionEventConditions.container;
import static org.junit.platform.testkit.ExecutionEventConditions.engine;
import static org.junit.platform.testkit.ExecutionEventConditions.event;
import static org.junit.platform.testkit.ExecutionEventConditions.finishedSuccessfully;
import static org.junit.platform.testkit.ExecutionEventConditions.finishedWithFailure;
import static org.junit.platform.testkit.ExecutionEventConditions.nestedContainer;
import static org.junit.platform.testkit.ExecutionEventConditions.started;
import static org.junit.platform.testkit.ExecutionEventConditions.test;
import static org.junit.platform.testkit.TestExecutionResultConditions.isA;
import static org.junit.platform.testkit.TestExecutionResultConditions.message;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestInstanceFactory;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstantiationException;
import org.junit.jupiter.engine.AbstractJupiterTestEngineTests;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.testkit.ExecutionResults;

/**
 * Integration tests that verify support for {@link TestInstanceFactory}.
 *
 * @since 5.3
 */
class TestInstanceFactoryTests extends AbstractJupiterTestEngineTests {

	private static final List<String> callSequence = new ArrayList<>();

	@BeforeEach
	void resetCallSequence() {
		callSequence.clear();
	}

	@Test
	void multipleFactoriesRegisteredOnSingleTestClass() {
		Class<?> testClass = MultipleFactoriesRegisteredOnSingleTestCase.class;
		ExecutionResults executionResults = executeTestsForClass(testClass).getExecutionResults();

		assertEquals(0, executionResults.getTestStartedCount(), "# tests started");
		assertEquals(0, executionResults.getTestFailedCount(), "# tests aborted");

		assertRecordedExecutionEventsContainsExactly(executionResults.getExecutionEvents(), //
			event(engine(), started()), //
			event(container(testClass), started()), //
			event(container(testClass),
				finishedWithFailure(allOf(isA(ExtensionConfigurationException.class),
					message("The following TestInstanceFactory extensions were registered for test class ["
							+ testClass.getName() + "], but only one is permitted: "
							+ nullSafeToString(FooInstanceFactory.class, BarInstanceFactory.class))))), //
			event(engine(), finishedSuccessfully()));
	}

	@Test
	void multipleFactoriesRegisteredWithinTestClassHierarchy() {
		Class<?> testClass = MultipleFactoriesRegisteredWithinClassHierarchyTestCase.class;
		ExecutionResults executionResults = executeTestsForClass(testClass).getExecutionResults();

		assertEquals(0, executionResults.getTestStartedCount(), "# tests started");
		assertEquals(0, executionResults.getTestFailedCount(), "# tests aborted");

		assertRecordedExecutionEventsContainsExactly(executionResults.getExecutionEvents(), //
			event(engine(), started()), //
			event(container(testClass), started()), //
			event(container(testClass),
				finishedWithFailure(allOf(isA(ExtensionConfigurationException.class),
					message("The following TestInstanceFactory extensions were registered for test class ["
							+ testClass.getName() + "], but only one is permitted: "
							+ nullSafeToString(FooInstanceFactory.class, BarInstanceFactory.class))))), //
			event(engine(), finishedSuccessfully()));
	}

	@Test
	void multipleFactoriesRegisteredWithinNestedClassStructure() {
		Class<?> outerClass = MultipleFactoriesRegisteredWithinNestedClassStructureTestCase.class;
		Class<?> nestedClass = MultipleFactoriesRegisteredWithinNestedClassStructureTestCase.InnerTestCase.class;
		ExecutionResults executionResults = executeTestsForClass(outerClass).getExecutionResults();

		assertEquals(1, executionResults.getTestStartedCount(), "# tests started");
		assertEquals(1, executionResults.getTestSuccessfulCount(), "# tests succeeded");

		assertRecordedExecutionEventsContainsExactly(executionResults.getExecutionEvents(), //
			event(engine(), started()), //
			event(container(outerClass), started()), //
			event(test("outerTest()"), started()), //
			event(test("outerTest()"), finishedSuccessfully()), //
			event(nestedContainer(nestedClass), started()), //
			event(nestedContainer(nestedClass),
				finishedWithFailure(allOf(isA(ExtensionConfigurationException.class),
					message("The following TestInstanceFactory extensions were registered for test class ["
							+ nestedClass.getName() + "], but only one is permitted: "
							+ nullSafeToString(FooInstanceFactory.class, BarInstanceFactory.class))))), //
			event(container(outerClass), finishedSuccessfully()), //
			event(engine(), finishedSuccessfully()));
	}

	@Test
	void nullTestInstanceFactoryWithPerMethodLifecycle() {
		Class<?> testClass = NullTestInstanceFactoryTestCase.class;
		ExecutionResults executionResults = executeTestsForClass(testClass).getExecutionResults();

		assertEquals(1, executionResults.getTestStartedCount(), "# tests started");
		assertEquals(1, executionResults.getTestFailedCount(), "# tests aborted");

		assertRecordedExecutionEventsContainsExactly(executionResults.getExecutionEvents(), //
			event(engine(), started()), //
			event(container(testClass), started()), //
			event(test("testShouldNotBeCalled"), started()), //
			event(test("testShouldNotBeCalled"),
				finishedWithFailure(allOf(isA(TestInstantiationException.class),
					message(m -> m.equals("TestInstanceFactory [" + NullTestInstanceFactory.class.getName()
							+ "] failed to return an instance of [" + testClass.getName()
							+ "] and instead returned an instance of [null]."))))), //
			event(container(testClass), finishedSuccessfully()), //
			event(engine(), finishedSuccessfully()));
	}

	@Test
	void nullTestInstanceFactoryWithPerClassLifecycle() {
		Class<?> testClass = PerClassLifecycleNullTestInstanceFactoryTestCase.class;
		ExecutionResults executionResults = executeTestsForClass(testClass).getExecutionResults();

		assertEquals(0, executionResults.getTestStartedCount(), "# tests started");
		assertEquals(0, executionResults.getTestFailedCount(), "# tests aborted");

		assertRecordedExecutionEventsContainsExactly(executionResults.getExecutionEvents(), //
			event(engine(), started()), //
			event(container(testClass), started()), //
			event(container(testClass),
				finishedWithFailure(allOf(isA(TestInstantiationException.class),
					message(m -> m.equals("TestInstanceFactory [" + NullTestInstanceFactory.class.getName()
							+ "] failed to return an instance of [" + testClass.getName()
							+ "] and instead returned an instance of [null]."))))), //
			event(engine(), finishedSuccessfully()));
	}

	@Test
	void bogusTestInstanceFactoryWithPerMethodLifecycle() {
		Class<?> testClass = BogusTestInstanceFactoryTestCase.class;
		ExecutionResults executionResults = executeTestsForClass(testClass).getExecutionResults();

		assertEquals(1, executionResults.getTestStartedCount(), "# tests started");
		assertEquals(1, executionResults.getTestFailedCount(), "# tests aborted");

		assertRecordedExecutionEventsContainsExactly(executionResults.getExecutionEvents(), //
			event(engine(), started()), //
			event(container(testClass), started()), //
			event(test("testShouldNotBeCalled"), started()), //
			event(test("testShouldNotBeCalled"),
				finishedWithFailure(allOf(isA(TestInstantiationException.class),
					message(m -> m.equals("TestInstanceFactory [" + BogusTestInstanceFactory.class.getName()
							+ "] failed to return an instance of [" + testClass.getName()
							+ "] and instead returned an instance of [java.lang.String]."))))), //
			event(container(testClass), finishedSuccessfully()), //
			event(engine(), finishedSuccessfully()));
	}

	@Test
	void bogusTestInstanceFactoryWithPerClassLifecycle() {
		Class<?> testClass = PerClassLifecycleBogusTestInstanceFactoryTestCase.class;
		ExecutionResults executionResults = executeTestsForClass(testClass).getExecutionResults();

		assertEquals(0, executionResults.getTestStartedCount(), "# tests started");
		assertEquals(0, executionResults.getTestFailedCount(), "# tests aborted");

		assertRecordedExecutionEventsContainsExactly(executionResults.getExecutionEvents(), //
			event(engine(), started()), //
			event(container(testClass), started()), //
			event(container(testClass),
				finishedWithFailure(allOf(isA(TestInstantiationException.class),
					message(m -> m.equals("TestInstanceFactory [" + BogusTestInstanceFactory.class.getName()
							+ "] failed to return an instance of [" + testClass.getName()
							+ "] and instead returned an instance of [java.lang.String]."))))), //
			event(engine(), finishedSuccessfully()));
	}

	@Test
	void explosiveTestInstanceFactoryWithPerMethodLifecycle() {
		Class<?> testClass = ExplosiveTestInstanceFactoryTestCase.class;
		ExecutionResults executionResults = executeTestsForClass(testClass).getExecutionResults();

		assertEquals(1, executionResults.getTestStartedCount(), "# tests started");
		assertEquals(1, executionResults.getTestFailedCount(), "# tests aborted");

		assertRecordedExecutionEventsContainsExactly(executionResults.getExecutionEvents(), //
			event(engine(), started()), //
			event(container(testClass), started()), //
			event(test("testShouldNotBeCalled"), started()), //
			event(test("testShouldNotBeCalled"),
				finishedWithFailure(allOf(isA(TestInstantiationException.class),
					message("TestInstanceFactory [" + ExplosiveTestInstanceFactory.class.getName()
							+ "] failed to instantiate test class [" + testClass.getName() + "]: boom!")))), //
			event(container(testClass), finishedSuccessfully()), //
			event(engine(), finishedSuccessfully()));
	}

	@Test
	void explosiveTestInstanceFactoryWithPerClassLifecycle() {
		Class<?> testClass = PerClassLifecycleExplosiveTestInstanceFactoryTestCase.class;
		ExecutionResults executionResults = executeTestsForClass(testClass).getExecutionResults();

		assertEquals(0, executionResults.getTestStartedCount(), "# tests started");
		assertEquals(0, executionResults.getTestFailedCount(), "# tests aborted");

		assertRecordedExecutionEventsContainsExactly(executionResults.getExecutionEvents(), //
			event(engine(), started()), //
			event(container(testClass), started()), //
			event(container(testClass), //
				finishedWithFailure(allOf(isA(TestInstantiationException.class),
					message("TestInstanceFactory [" + ExplosiveTestInstanceFactory.class.getName()
							+ "] failed to instantiate test class [" + testClass.getName() + "]: boom!")))), //
			event(engine(), finishedSuccessfully()));
	}

	@Test
	void proxyTestInstanceFactoryFailsDueToUseOfDifferentClassLoader() {
		Class<?> testClass = ProxiedTestCase.class;
		ExecutionResults executionResults = executeTestsForClass(testClass).getExecutionResults();

		assertEquals(0, executionResults.getTestStartedCount(), "# tests started");
		assertEquals(0, executionResults.getTestFailedCount(), "# tests aborted");

		assertRecordedExecutionEventsContainsExactly(executionResults.getExecutionEvents(), //
			event(engine(), started()), //
			event(container(testClass), started()), //
			event(container(testClass), //
				// NOTE: the test class names are the same even though the objects are
				// instantiated using different ClassLoaders. Thus, we check for the
				// appended "@" but ignore the actual hash code for the test class
				// loaded by the different ClassLoader.
				finishedWithFailure(allOf(isA(TestInstantiationException.class),
					message(m -> m.startsWith("TestInstanceFactory [" + ProxyTestInstanceFactory.class.getName() + "]")
							&& m.contains("failed to return an instance of [" + testClass.getName() + "@"
									+ Integer.toHexString(System.identityHashCode(testClass)))
							&& m.contains("and instead returned an instance of [" + testClass.getName() + "@")//
					)))), //
			event(engine(), finishedSuccessfully()));
	}

	@Test
	void instanceFactoryOnTopLevelTestClass() {
		ExecutionResults executionResults = executeTestsForClass(ParentTestCase.class).getExecutionResults();

		assertEquals(1, executionResults.getTestStartedCount(), "# tests started");
		assertEquals(1, executionResults.getTestSuccessfulCount(), "# tests succeeded");

		// @formatter:off
		assertThat(callSequence).containsExactly(
			"FooInstanceFactory instantiated: ParentTestCase",
				"parentTest"
		);
		// @formatter:on
	}

	@Test
	void inheritedFactoryInTestClassHierarchy() {
		ExecutionResults executionResults = executeTestsForClass(InheritedFactoryTestCase.class).getExecutionResults();

		assertEquals(2, executionResults.getTestStartedCount(), "# tests started");
		assertEquals(2, executionResults.getTestSuccessfulCount(), "# tests succeeded");

		// @formatter:off
		assertThat(callSequence).containsExactly(
			"FooInstanceFactory instantiated: InheritedFactoryTestCase",
				"parentTest",
			"FooInstanceFactory instantiated: InheritedFactoryTestCase",
				"childTest"
		);
		// @formatter:on
	}

	@Test
	void instanceFactoriesInNestedClassStructureAreInherited() {
		ExecutionResults executionResults = executeTestsForClass(OuterTestCase.class).getExecutionResults();

		assertEquals(3, executionResults.getTestStartedCount(), "# tests started");
		assertEquals(3, executionResults.getTestSuccessfulCount(), "# tests succeeded");

		// @formatter:off
		assertThat(callSequence).containsExactly(

			// OuterTestCase
			"FooInstanceFactory instantiated: OuterTestCase",
				"outerTest",

			// InnerTestCase
			"FooInstanceFactory instantiated: OuterTestCase",
				"FooInstanceFactory instantiated: InnerTestCase",
					"innerTest1",

			// InnerInnerTestCase
			"FooInstanceFactory instantiated: OuterTestCase",
				"FooInstanceFactory instantiated: InnerTestCase",
					"FooInstanceFactory instantiated: InnerInnerTestCase",
						"innerTest2"
		);
		// @formatter:on
	}

	@Test
	void instanceFactoryRegisteredViaTestInterface() {
		ExecutionResults executionResults = executeTestsForClass(
			FactoryFromInterfaceTestCase.class).getExecutionResults();

		assertEquals(1, executionResults.getTestStartedCount(), "# tests started");
		assertEquals(1, executionResults.getTestSuccessfulCount(), "# tests succeeded");

		// @formatter:off
		assertThat(callSequence).containsExactly(
			"FooInstanceFactory instantiated: FactoryFromInterfaceTestCase",
				"test"
		);
		// @formatter:on
	}

	@Test
	void instanceFactoryRegisteredAsLambdaExpression() {
		ExecutionResults executionResults = executeTestsForClass(LambdaFactoryTestCase.class).getExecutionResults();

		assertEquals(1, executionResults.getTestStartedCount(), "# tests started");
		assertEquals(1, executionResults.getTestSuccessfulCount(), "# tests succeeded");

		// @formatter:off
		assertThat(callSequence).containsExactly(
			"beforeEach: lambda",
				"test: lambda"
		);
		// @formatter:on
	}

	@Test
	void instanceFactoryWithPerClassLifecycle() {
		ExecutionResults executionResults = executeTestsForClass(PerClassLifecycleTestCase.class).getExecutionResults();

		assertEquals(1, PerClassLifecycleTestCase.counter.get());

		assertEquals(2, executionResults.getTestStartedCount(), "# tests started");
		assertEquals(2, executionResults.getTestSuccessfulCount(), "# tests succeeded");

		// @formatter:off
		assertThat(callSequence).containsExactly(
			"FooInstanceFactory instantiated: PerClassLifecycleTestCase",
				"@BeforeAll",
					"@BeforeEach",
						"test1",
					"@BeforeEach",
						"test2",
				"@AfterAll"
		);
		// @formatter:on
	}

	// -------------------------------------------------------------------------

	@ExtendWith({ FooInstanceFactory.class, BarInstanceFactory.class })
	static class MultipleFactoriesRegisteredOnSingleTestCase {

		@Test
		void testShouldNotBeCalled() {
			callSequence.add("testShouldNotBeCalled");
		}
	}

	@ExtendWith(NullTestInstanceFactory.class)
	static class NullTestInstanceFactoryTestCase {

		@Test
		void testShouldNotBeCalled() {
			callSequence.add("testShouldNotBeCalled");
		}
	}

	@TestInstance(PER_CLASS)
	static class PerClassLifecycleNullTestInstanceFactoryTestCase extends NullTestInstanceFactoryTestCase {
	}

	@ExtendWith(BogusTestInstanceFactory.class)
	static class BogusTestInstanceFactoryTestCase {

		@Test
		void testShouldNotBeCalled() {
			callSequence.add("testShouldNotBeCalled");
		}
	}

	@TestInstance(PER_CLASS)
	static class PerClassLifecycleBogusTestInstanceFactoryTestCase extends BogusTestInstanceFactoryTestCase {
	}

	@ExtendWith(ExplosiveTestInstanceFactory.class)
	static class ExplosiveTestInstanceFactoryTestCase {

		@Test
		void testShouldNotBeCalled() {
			callSequence.add("testShouldNotBeCalled");
		}
	}

	@TestInstance(PER_CLASS)
	static class PerClassLifecycleExplosiveTestInstanceFactoryTestCase extends ExplosiveTestInstanceFactoryTestCase {
	}

	@ExtendWith(FooInstanceFactory.class)
	static class ParentTestCase {

		@Test
		void parentTest() {
			callSequence.add("parentTest");
		}
	}

	static class InheritedFactoryTestCase extends ParentTestCase {

		@Test
		void childTest() {
			callSequence.add("childTest");
		}
	}

	@ExtendWith(BarInstanceFactory.class)
	static class MultipleFactoriesRegisteredWithinClassHierarchyTestCase extends ParentTestCase {

		@Test
		void childTest() {
			callSequence.add("childTest");
		}
	}

	@ExtendWith(FooInstanceFactory.class)
	static class OuterTestCase {

		@Test
		void outerTest() {
			callSequence.add("outerTest");
		}

		@Nested
		class InnerTestCase {

			@Test
			void innerTest1() {
				callSequence.add("innerTest1");
			}

			@Nested
			class InnerInnerTestCase {

				@Test
				void innerTest2() {
					callSequence.add("innerTest2");
				}
			}
		}
	}

	@ExtendWith(FooInstanceFactory.class)
	static class MultipleFactoriesRegisteredWithinNestedClassStructureTestCase {

		@Test
		void outerTest() {
		}

		@Nested
		@ExtendWith(BarInstanceFactory.class)
		class InnerTestCase {

			@Test
			void innerTest() {
			}
		}
	}

	@ExtendWith(FooInstanceFactory.class)
	interface TestInterface {
	}

	static class FactoryFromInterfaceTestCase implements TestInterface {

		@Test
		void test() {
			callSequence.add("test");
		}
	}

	static class LambdaFactoryTestCase {

		private final String text;

		@RegisterExtension
		static final TestInstanceFactory factory = (__, ___) -> new LambdaFactoryTestCase("lambda");

		LambdaFactoryTestCase(String text) {
			this.text = text;
		}

		@BeforeEach
		void beforeEach() {
			callSequence.add("beforeEach: " + this.text);
		}

		@Test
		void test() {
			callSequence.add("test: " + this.text);
		}
	}

	@ExtendWith(FooInstanceFactory.class)
	@TestInstance(PER_CLASS)
	static class PerClassLifecycleTestCase {

		static final AtomicInteger counter = new AtomicInteger();

		PerClassLifecycleTestCase() {
			counter.incrementAndGet();
		}

		@BeforeAll
		void beforeAll() {
			callSequence.add("@BeforeAll");
		}

		@BeforeEach
		void beforeEach() {
			callSequence.add("@BeforeEach");
		}

		@Test
		void test1() {
			callSequence.add("test1");
		}

		@Test
		void test2() {
			callSequence.add("test2");
		}

		@AfterAll
		void afterAll() {
			callSequence.add("@AfterAll");
		}
	}

	@ExtendWith(ProxyTestInstanceFactory.class)
	@TestInstance(PER_CLASS)
	static class ProxiedTestCase {

		@Test
		void test1() {
			callSequence.add("test1");
		}

		@Test
		void test2() {
			callSequence.add("test2");
		}
	}

	// -------------------------------------------------------------------------

	private static abstract class AbstractTestInstanceFactory implements TestInstanceFactory {

		@Override
		public Object createTestInstance(TestInstanceFactoryContext factoryContext, ExtensionContext extensionContext) {
			Class<?> testClass = factoryContext.getTestClass();
			instantiated(getClass(), testClass);

			if (factoryContext.getOuterInstance().isPresent()) {
				return ReflectionUtils.newInstance(testClass, factoryContext.getOuterInstance().get());
			}
			// else
			return ReflectionUtils.newInstance(testClass);
		}
	}

	private static class FooInstanceFactory extends AbstractTestInstanceFactory {
	}

	private static class BarInstanceFactory extends AbstractTestInstanceFactory {
	}

	/**
	 * {@link TestInstanceFactory} that returns null.
	 */
	private static class NullTestInstanceFactory implements TestInstanceFactory {

		@Override
		public Object createTestInstance(TestInstanceFactoryContext factoryContext, ExtensionContext extensionContext) {
			return null;
		}
	}

	/**
	 * {@link TestInstanceFactory} that returns an object of a type that does
	 * not match the supplied test class.
	 */
	private static class BogusTestInstanceFactory implements TestInstanceFactory {

		@Override
		public Object createTestInstance(TestInstanceFactoryContext factoryContext, ExtensionContext extensionContext) {
			return "bogus";
		}
	}

	/**
	 * {@link TestInstanceFactory} that always throws an exception.
	 */
	private static class ExplosiveTestInstanceFactory implements TestInstanceFactory {

		@Override
		public Object createTestInstance(TestInstanceFactoryContext factoryContext, ExtensionContext extensionContext) {
			throw new RuntimeException("boom!");
		}
	}

	/**
	 * This does not actually create a proxy. Rather, it simply simulates what
	 * a proxy-based implementation might do, by loading the class from a
	 * different {@link ClassLoader}.
	 */
	private static class ProxyTestInstanceFactory implements TestInstanceFactory {

		@Override
		public Object createTestInstance(TestInstanceFactoryContext factoryContext, ExtensionContext extensionContext) {
			Class<?> testClass = factoryContext.getTestClass();
			String className = testClass.getName();
			instantiated(getClass(), testClass);

			try (ProxyClassLoader proxyClassLoader = new ProxyClassLoader()) {
				// Load test class from different class loader
				Class<?> clazz = proxyClassLoader.loadClass(className);
				return ReflectionUtils.newInstance(clazz);
			}
			catch (Exception ex) {
				throw new RuntimeException("Failed to load class [" + className + "]", ex);
			}
		}
	}

	private static class ProxyClassLoader extends URLClassLoader {

		ProxyClassLoader() {
			super(new URL[] { ProxyClassLoader.class.getProtectionDomain().getCodeSource().getLocation() },
				getSystemClassLoader());
		}

		@Override
		public Class<?> loadClass(String name) throws ClassNotFoundException {
			return (name.startsWith(TestInstanceFactoryTests.class.getName()) ? findClass(name)
					: super.loadClass(name));
		}
	}

	private static boolean instantiated(Class<? extends TestInstanceFactory> factoryClass, Class<?> testClass) {
		return callSequence.add(factoryClass.getSimpleName() + " instantiated: " + testClass.getSimpleName());
	}

}
