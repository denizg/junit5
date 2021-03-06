/*
 * Copyright 2015-2018 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.jupiter.engine.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.engine.AbstractJupiterTestEngineTests;
import org.junit.jupiter.engine.execution.injection.sample.PrimitiveArrayParameterResolver;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.testkit.ExecutionEvent;
import org.junit.platform.testkit.ExecutionResults;

/**
 * Integration tests for {@link UniqueId#parse(String)} for methods
 * with array type parameters.
 *
 * @see <a href="https://github.com/junit-team/junit5/issues/810">#810</a>
 * @see org.junit.platform.engine.UniqueIdTests
 *
 * @since 5.0
 */
class UniqueIdParsingForArrayParameterIntegrationTests extends AbstractJupiterTestEngineTests {

	@Test
	void executeTestsForPrimitiveArrayMethodInjectionCases() {
		ExecutionResults executionResults = executeTestsForClass(
			PrimitiveArrayMethodInjectionTestCase.class).getExecutionResults();

		assertEquals(1, executionResults.getTestStartedCount(), "# tests started");
		assertEquals(1, executionResults.getTestSuccessfulCount(), "# tests succeeded");
		assertEquals(0, executionResults.getTestFailedCount(), "# tests failed");

		// @formatter:off
		UniqueId uniqueId = executionResults.getExecutionEvents().stream()
				.map(ExecutionEvent::getTestDescriptor)
				.distinct()
				.skip(2)
				.map(TestDescriptor::getUniqueId)
				.findFirst()
				.orElseThrow(AssertionError::new);
		// @formatter:on

		assertThat(UniqueId.parse(uniqueId.toString())).isEqualTo(uniqueId);
	}

	@ExtendWith(PrimitiveArrayParameterResolver.class)
	static class PrimitiveArrayMethodInjectionTestCase {

		@Test
		void primitiveArray(int... ints) {
			assertArrayEquals(new int[] { 1, 2, 3 }, ints);
		}
	}

}
