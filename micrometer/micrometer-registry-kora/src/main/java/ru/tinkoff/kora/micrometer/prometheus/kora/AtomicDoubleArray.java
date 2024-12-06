/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 adouble with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package ru.tinkoff.kora.micrometer.prometheus.kora;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

/**
 * A {@code double} array in which elements may be updated atomically.
 * See the {@link VarHandle} specification for descriptions of the
 * properties of atomic accesses.
 * <p>
 * Credits to author Doug Lea
 * Based on {@link java.util.concurrent.atomic.AtomicLongArray}
 */
public class AtomicDoubleArray implements java.io.Serializable {

    private static final VarHandle AA = MethodHandles.arrayElementVarHandle(double[].class);
    private final double[] array;

    /**
     * Creates a new AtomicdoubleArray of the given length, with all
     * elements initially zero.
     *
     * @param length the length of the array
     */
    public AtomicDoubleArray(int length) {
        array = new double[length];
    }

    /**
     * Creates a new AtomicdoubleArray with the same length as, and
     * all elements copied from, the given array.
     *
     * @param array the array to copy elements from
     * @throws NullPointerException if array is null
     */
    public AtomicDoubleArray(double[] array) {
        // Visibility guaranteed by final field guarantees
        this.array = array.clone();
    }

    /**
     * Returns the length of the array.
     *
     * @return the length of the array
     */
    public final int length() {
        return array.length;
    }

    /**
     * Returns the current value of the element at index {@code i},
     * with memory effects as specified by {@link VarHandle#getVolatile}.
     *
     * @param i the index
     * @return the current value
     */
    public final double get(int i) {
        return (double) AA.getVolatile(array, i);
    }

    /**
     * Sets the element at index {@code i} to {@code newValue},
     * with memory effects as specified by {@link VarHandle#setVolatile}.
     *
     * @param i        the index
     * @param newValue the new value
     */
    public final void set(int i, double newValue) {
        AA.setVolatile(array, i, newValue);
    }

    /**
     * Sets the element at index {@code i} to {@code newValue},
     * with memory effects as specified by {@link VarHandle#setRelease}.
     *
     * @param i        the index
     * @param newValue the new value
     * @since 1.6
     */
    public final void lazySet(int i, double newValue) {
        AA.setRelease(array, i, newValue);
    }

    /**
     * Atomically sets the element at index {@code i} to {@code
     * newValue} and returns the old value,
     * with memory effects as specified by {@link VarHandle#getAndSet}.
     *
     * @param i        the index
     * @param newValue the new value
     * @return the previous value
     */
    public final double getAndSet(int i, double newValue) {
        return (double) AA.getAndSet(array, i, newValue);
    }

    /**
     * Atomically sets the element at index {@code i} to {@code newValue}
     * if the element's current value {@code == expectedValue},
     * with memory effects as specified by {@link VarHandle#compareAndSet}.
     *
     * @param i             the index
     * @param expectedValue the expected value
     * @param newValue      the new value
     * @return {@code true} if successful. False return indicates that
     * the actual value was not equal to the expected value.
     */
    public final boolean compareAndSet(int i, double expectedValue, double newValue) {
        return AA.compareAndSet(array, i, expectedValue, newValue);
    }

    /**
     * Possibly atomically sets the element at index {@code i} to
     * {@code newValue} if the element's current value {@code == expectedValue},
     * with memory effects as specified by {@link VarHandle#weakCompareAndSetPlain}.
     *
     * @param i             the index
     * @param expectedValue the expected value
     * @param newValue      the new value
     * @return {@code true} if successful
     * @since 9
     */
    public final boolean weakCompareAndSetPlain(int i, double expectedValue, double newValue) {
        return AA.weakCompareAndSetPlain(array, i, expectedValue, newValue);
    }

    /**
     * Atomically increments the value of the element at index {@code i},
     * with memory effects as specified by {@link VarHandle#getAndAdd}.
     *
     * <p>Equivalent to {@code getAndAdd(i, 1)}.
     *
     * @param i the index
     * @return the previous value
     */
    public final double getAndIncrement(int i) {
        return (double) AA.getAndAdd(array, i, 1L);
    }

    /**
     * Atomically decrements the value of the element at index {@code i},
     * with memory effects as specified by {@link VarHandle#getAndAdd}.
     *
     * <p>Equivalent to {@code getAndAdd(i, -1)}.
     *
     * @param i the index
     * @return the previous value
     */
    public final double getAndDecrement(int i) {
        return (double) AA.getAndAdd(array, i, -1L);
    }

    /**
     * Atomically adds the given value to the element at index {@code i},
     * with memory effects as specified by {@link VarHandle#getAndAdd}.
     *
     * @param i     the index
     * @param delta the value to add
     * @return the previous value
     */
    public final double getAndAdd(int i, double delta) {
        return (double) AA.getAndAdd(array, i, delta);
    }

    /**
     * Atomically increments the value of the element at index {@code i},
     * with memory effects as specified by {@link VarHandle#getAndAdd}.
     *
     * <p>Equivalent to {@code addAndGet(i, 1)}.
     *
     * @param i the index
     * @return the updated value
     */
    public final double incrementAndGet(int i) {
        return (double) AA.getAndAdd(array, i, 1L) + 1L;
    }

    /**
     * Atomically decrements the value of the element at index {@code i},
     * with memory effects as specified by {@link VarHandle#getAndAdd}.
     *
     * <p>Equivalent to {@code addAndGet(i, -1)}.
     *
     * @param i the index
     * @return the updated value
     */
    public final double decrementAndGet(int i) {
        return (double) AA.getAndAdd(array, i, -1L) - 1L;
    }

    /**
     * Atomically adds the given value to the element at index {@code i},
     * with memory effects as specified by {@link VarHandle#getAndAdd}.
     *
     * @param i     the index
     * @param delta the value to add
     * @return the updated value
     */
    public double addAndGet(int i, double delta) {
        return (double) AA.getAndAdd(array, i, delta) + delta;
    }

    /**
     * Atomically updates (with memory effects as specified by {@link
     * VarHandle#compareAndSet}) the element at index {@code i} with
     * the results of applying the given function, returning the
     * previous value. The function should be side-effect-free, since
     * it may be re-applied when attempted updates fail due to
     * contention among threads.
     *
     * @param i              the index
     * @param updateFunction a side-effect-free function
     * @return the previous value
     * @since 1.8
     */
    public final double getAndUpdate(int i, DoubleUnaryOperator updateFunction) {
        double prev = get(i), next = 0L;
        for (boolean haveNext = false; ; ) {
            if (!haveNext)
                next = updateFunction.applyAsDouble(prev);
            if (weakCompareAndSetVolatile(i, prev, next))
                return prev;
            haveNext = (prev == (prev = get(i)));
        }
    }

    /**
     * Atomically updates (with memory effects as specified by {@link
     * VarHandle#compareAndSet}) the element at index {@code i} with
     * the results of applying the given function, returning the
     * updated value. The function should be side-effect-free, since it
     * may be re-applied when attempted updates fail due to contention
     * among threads.
     *
     * @param i              the index
     * @param updateFunction a side-effect-free function
     * @return the updated value
     * @since 1.8
     */
    public final double updateAndGet(int i, DoubleUnaryOperator updateFunction) {
        double prev = get(i), next = 0L;
        for (boolean haveNext = false; ; ) {
            if (!haveNext)
                next = updateFunction.applyAsDouble(prev);
            if (weakCompareAndSetVolatile(i, prev, next))
                return next;
            haveNext = (prev == (prev = get(i)));
        }
    }

    /**
     * Atomically updates (with memory effects as specified by {@link
     * VarHandle#compareAndSet}) the element at index {@code i} with
     * the results of applying the given function to the current and
     * given values, returning the previous value. The function should
     * be side-effect-free, since it may be re-applied when attempted
     * updates fail due to contention among threads.  The function is
     * applied with the current value of the element at index {@code i}
     * as its first argument, and the given update as the second
     * argument.
     *
     * @param i                   the index
     * @param x                   the update value
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @return the previous value
     * @since 1.8
     */
    public final double getAndAccumulate(int i,
                                         double x,
                                         DoubleBinaryOperator accumulatorFunction) {
        double prev = get(i), next = 0L;
        for (boolean haveNext = false; ; ) {
            if (!haveNext)
                next = accumulatorFunction.applyAsDouble(prev, x);
            if (weakCompareAndSetVolatile(i, prev, next))
                return prev;
            haveNext = (prev == (prev = get(i)));
        }
    }

    /**
     * Atomically updates (with memory effects as specified by {@link
     * VarHandle#compareAndSet}) the element at index {@code i} with
     * the results of applying the given function to the current and
     * given values, returning the updated value. The function should
     * be side-effect-free, since it may be re-applied when attempted
     * updates fail due to contention among threads.  The function is
     * applied with the current value of the element at index {@code i}
     * as its first argument, and the given update as the second
     * argument.
     *
     * @param i                   the index
     * @param x                   the update value
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @return the updated value
     * @since 1.8
     */
    public final double accumulateAndGet(int i,
                                         double x,
                                         DoubleBinaryOperator accumulatorFunction) {
        double prev = get(i), next = 0L;
        for (boolean haveNext = false; ; ) {
            if (!haveNext)
                next = accumulatorFunction.applyAsDouble(prev, x);
            if (weakCompareAndSetVolatile(i, prev, next))
                return next;
            haveNext = (prev == (prev = get(i)));
        }
    }

    /**
     * Returns the String representation of the current values of array.
     *
     * @return the String representation of the current values of array
     */
    public String toString() {
        int iMax = array.length - 1;
        if (iMax == -1)
            return "[]";

        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0; ; i++) {
            b.append(get(i));
            if (i == iMax)
                return b.append(']').toString();
            b.append(',').append(' ');
        }
    }

    // jdk9

    /**
     * Returns the current value of the element at index {@code i},
     * with memory semantics of reading as if the variable was declared
     * non-{@code volatile}.
     *
     * @param i the index
     * @return the value
     * @since 9
     */
    public final double getPlain(int i) {
        return (double) AA.get(array, i);
    }

    /**
     * Sets the element at index {@code i} to {@code newValue},
     * with memory semantics of setting as if the variable was
     * declared non-{@code volatile} and non-{@code final}.
     *
     * @param i        the index
     * @param newValue the new value
     * @since 9
     */
    public final void setPlain(int i, double newValue) {
        AA.set(array, i, newValue);
    }

    /**
     * Returns the current value of the element at index {@code i},
     * with memory effects as specified by {@link VarHandle#getOpaque}.
     *
     * @param i the index
     * @return the value
     * @since 9
     */
    public final double getOpaque(int i) {
        return (double) AA.getOpaque(array, i);
    }

    /**
     * Sets the element at index {@code i} to {@code newValue},
     * with memory effects as specified by {@link VarHandle#setOpaque}.
     *
     * @param i        the index
     * @param newValue the new value
     * @since 9
     */
    public final void setOpaque(int i, double newValue) {
        AA.setOpaque(array, i, newValue);
    }

    /**
     * Returns the current value of the element at index {@code i},
     * with memory effects as specified by {@link VarHandle#getAcquire}.
     *
     * @param i the index
     * @return the value
     * @since 9
     */
    public final double getAcquire(int i) {
        return (double) AA.getAcquire(array, i);
    }

    /**
     * Sets the element at index {@code i} to {@code newValue},
     * with memory effects as specified by {@link VarHandle#setRelease}.
     *
     * @param i        the index
     * @param newValue the new value
     * @since 9
     */
    public final void setRelease(int i, double newValue) {
        AA.setRelease(array, i, newValue);
    }

    /**
     * Atomically sets the element at index {@code i} to {@code newValue}
     * if the element's current value, referred to as the <em>witness
     * value</em>, {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#compareAndExchange}.
     *
     * @param i             the index
     * @param expectedValue the expected value
     * @param newValue      the new value
     * @return the witness value, which will be the same as the
     * expected value if successful
     * @since 9
     */
    public final double compareAndExchange(int i, double expectedValue, double newValue) {
        return (double) AA.compareAndExchange(array, i, expectedValue, newValue);
    }

    /**
     * Atomically sets the element at index {@code i} to {@code newValue}
     * if the element's current value, referred to as the <em>witness
     * value</em>, {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#compareAndExchangeAcquire}.
     *
     * @param i             the index
     * @param expectedValue the expected value
     * @param newValue      the new value
     * @return the witness value, which will be the same as the
     * expected value if successful
     * @since 9
     */
    public final double compareAndExchangeAcquire(int i, double expectedValue, double newValue) {
        return (double) AA.compareAndExchangeAcquire(array, i, expectedValue, newValue);
    }

    /**
     * Atomically sets the element at index {@code i} to {@code newValue}
     * if the element's current value, referred to as the <em>witness
     * value</em>, {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#compareAndExchangeRelease}.
     *
     * @param i             the index
     * @param expectedValue the expected value
     * @param newValue      the new value
     * @return the witness value, which will be the same as the
     * expected value if successful
     * @since 9
     */
    public final double compareAndExchangeRelease(int i, double expectedValue, double newValue) {
        return (double) AA.compareAndExchangeRelease(array, i, expectedValue, newValue);
    }

    /**
     * Possibly atomically sets the element at index {@code i} to
     * {@code newValue} if the element's current value {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#weakCompareAndSet}.
     *
     * @param i             the index
     * @param expectedValue the expected value
     * @param newValue      the new value
     * @return {@code true} if successful
     * @since 9
     */
    public final boolean weakCompareAndSetVolatile(int i, double expectedValue, double newValue) {
        return AA.weakCompareAndSet(array, i, expectedValue, newValue);
    }

    /**
     * Possibly atomically sets the element at index {@code i} to
     * {@code newValue} if the element's current value {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#weakCompareAndSetAcquire}.
     *
     * @param i             the index
     * @param expectedValue the expected value
     * @param newValue      the new value
     * @return {@code true} if successful
     * @since 9
     */
    public final boolean weakCompareAndSetAcquire(int i, double expectedValue, double newValue) {
        return AA.weakCompareAndSetAcquire(array, i, expectedValue, newValue);
    }

    /**
     * Possibly atomically sets the element at index {@code i} to
     * {@code newValue} if the element's current value {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#weakCompareAndSetRelease}.
     *
     * @param i             the index
     * @param expectedValue the expected value
     * @param newValue      the new value
     * @return {@code true} if successful
     * @since 9
     */
    public final boolean weakCompareAndSetRelease(int i, double expectedValue, double newValue) {
        return AA.weakCompareAndSetRelease(array, i, expectedValue, newValue);
    }
}
