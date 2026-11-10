package com.springsentry.sample;

public class Calculator {

	public int add(int left, int right) {
		return left + right;
	}

	public int divide(int numerator, int denominator) {
		if (denominator == 0) {
			throw new IllegalArgumentException("Denominator must not be zero");
		}
		return numerator / denominator;
	}
}
