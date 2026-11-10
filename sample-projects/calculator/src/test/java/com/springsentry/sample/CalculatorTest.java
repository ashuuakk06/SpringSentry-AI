package com.springsentry.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CalculatorTest {

	private final Calculator calculator = new Calculator();

	@Test
	void addsTwoNumbers() {
		assertEquals(7, calculator.add(3, 4));
	}

	@Test
	void rejectsDivisionByZero() {
		assertThrows(IllegalArgumentException.class, () -> calculator.divide(10, 0));
	}
}
