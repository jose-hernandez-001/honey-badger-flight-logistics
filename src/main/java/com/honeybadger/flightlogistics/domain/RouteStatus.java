/*
 * MIT License
 *
 * Copyright (c) 2026 José Hernández
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.honeybadger.flightlogistics.domain;

/**
 * Lifecycle states that a delivery route can occupy.
 *
 * <p>Routes progress through these states as operators plan, launch, manage,
 * and conclude delivery runs:
 * <ol>
 *   <li>{@link #PLANNED}   – route has been created and is awaiting dispatch.</li>
 *   <li>{@link #ACTIVE}    – route is currently in flight / being executed.</li>
 *   <li>{@link #PAUSED}    – route is temporarily suspended pending resumption.</li>
 *   <li>{@link #COMPLETED} – route reached its destination successfully.</li>
 *   <li>{@link #ABORTED}   – route was cancelled before completion.</li>
 * </ol>
 */
public enum RouteStatus {

    /** Route has been created and is awaiting dispatch. */
    PLANNED,

    /** Route is currently in flight / being executed. */
    ACTIVE,

    /** Route is temporarily suspended pending resumption. */
    PAUSED,

    /** Route reached its destination successfully. */
    COMPLETED,

    /** Route was cancelled before completion. */
    ABORTED
}
