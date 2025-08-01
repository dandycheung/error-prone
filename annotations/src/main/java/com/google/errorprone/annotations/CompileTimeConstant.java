/*
 * Copyright 2015 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.annotations;

import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation for method parameter and class field declarations, which denotes that corresponding
 * actual values must be compile-time constant expressions.
 *
 * <p>When the formal parameter of a method or constructor is annotated with the {@link
 * CompileTimeConstant} type annotation, the corresponding actual parameter must be an expression
 * that satisfies one of the following conditions:
 *
 * <ol>
 *   <li>The expression is one for which the Java compiler can determine a constant value at compile
 *       time, or
 *   <li>the expression consists of the literal {@code null}, or
 *   <li>the expression consists of a single identifier, where the identifier is a formal method
 *       parameter or class field that is declared {@code final} and has the {@link
 *       CompileTimeConstant} annotation, or
 *   <li>the expression is a {@link String}, and formed from the concatenation of symbols which meet
 *       these conditions, or
 *   <li>the expression is a ternary condition, where both branches satisfy these conditions, or
 *   <li>the expression is a switch expression, where every case is either a constant expression or
 *       throws, or
 *   <li>the expression is an immutable collection with all values known to satisfy these conditions
 *       (for example, {@code ImmutableSet.of("a", "b", "c")}).
 * </ol>
 *
 * <p>For example, the following code snippet is legal:
 *
 * <pre>{@code
 * public class C {
 *   private static final String S = "Hello";
 *   void m(@CompileTimeConstant final String s) {}
 *   void n(@CompileTimeConstant final String t) {
 *     m(S + " World!");
 *     m(null);
 *     m(t);
 *   }
 *   void o(boolean enabled) {
 *     m(enabled ? "on" : "off");
 *   }
 * }
 * }</pre>
 *
 * <p>In contrast, the following is illegal:
 *
 * <pre>{@code
 * public class C {
 *   void m(@CompileTimeConstant final String s) {}
 *   void n(String t) {
 *     m(t);
 *   }
 * }
 * }</pre>
 *
 * <p>When a class field is annotated with the {@link CompileTimeConstant} type annotation, the
 * field must also be declared to be {@code final}, and the corresponding initialised value must be
 * an expression that satisfies the conditions above.
 *
 * <p>For example, the following code snippet is legal:
 *
 * <pre>{@code
 * public class C {
 *   @CompileTimeConstant final String s;
 *   public C(@CompileTimeConstant String s) {
 *     this.s = s;
 *   }
 *   void m(@CompileTimeConstant final String s) {}
 *   void n() {
 *     m(s);
 *   }
 * }
 * }</pre>
 *
 * <p>In contrast, the following are illegal:
 *
 * <pre>{@code
 * public class C {
 *   @CompileTimeConstant String S;
 *   public C(@CompileTimeConstant String s) {
 *     this.S = s;
 *   }
 *   void m(@CompileTimeConstant final String s) { }
 *   void n() {
 *     m(S);
 *   }
 * }
 * }</pre>
 *
 * <pre>{@code
 * public class C {
 *   @CompileTimeConstant final String S;
 *   public C(String s) {
 *     this.S = s;
 *   }
 * }
 * }</pre>
 *
 * <p>Compile-time constant values are implicitly under the control of the trust domain of the
 * application whose source code they are part of. Hence, this annotation is useful to constrain the
 * use of APIs that may only be safely called with values that are under application control.
 *
 * <p>The current implementation of the @CompileTimeConstant checker cannot reason about more
 * complex scenarios, for example, returning compile-time-constant values from a method, or storing
 * compile-time-constant values in a collection. APIs will typically accommodate such use cases via
 * domain-specific types that capture domain-specific aspects of trustworthiness that arise from
 * values being under application control.
 *
 * <p>These constraints are enforced by <a href="https://errorprone.info">error-prone</a>.
 */
@Documented
@Retention(CLASS)
// TODO(xtof): We'd like to eventually support other cases, but I first need to determine with
// confidence that the checker can ensure all initializations and assignments to such variables are
// compile-time-constant.
@Target({ElementType.PARAMETER, ElementType.FIELD})
public @interface CompileTimeConstant {}
