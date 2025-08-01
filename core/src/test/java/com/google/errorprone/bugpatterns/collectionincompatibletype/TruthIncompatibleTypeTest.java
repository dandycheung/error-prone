/*
 * Copyright 2018 The Error Prone Authors.
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

package com.google.errorprone.bugpatterns.collectionincompatibletype;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static java.lang.String.format;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.extensions.proto.IterableOfProtosSubject;
import com.google.common.truth.extensions.proto.ProtoSubject;
import com.google.errorprone.CompilationTestHelper;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.Test;
import org.junit.runner.RunWith;

/** {@link TruthIncompatibleType}Test */
@RunWith(TestParameterInjector.class)
public class TruthIncompatibleTypeTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(TruthIncompatibleType.class, getClass());

  @Test
  public void positive() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;

            public class Test {
              static final class A {}

              static final class B {}

              public void f(A a, B b) {
                // BUG: Diagnostic contains:
                assertThat(a).isEqualTo(b);
                // BUG: Diagnostic contains:
                assertThat(a).isNotEqualTo(b);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void assumeTypeCheck() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.TruthJUnit.assume;

            public class Test {
              static final class A {}

              static final class B {}

              public void f(A a, B b) {
                // BUG: Diagnostic contains:
                assume().that(a).isEqualTo(b);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void negative() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;

            public class Test {
              static final class A {}

              static final class B {}

              public void f(A a, B b) {
                assertThat(a).isEqualTo(a);
                assertThat(b).isEqualTo(b);
                assertThat("a").isEqualTo("b");
              }
            }
            """)
        .doTest();
  }

  @Test
  public void mixedNumberTypes_noMatch() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;

            public class Test {
              public void f() {
                assertThat(2L).isEqualTo(2);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void mixedBoxedNumberTypes_noMatch() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;

            public class Test {
              public void f() {
                assertThat(Byte.valueOf((byte) 2)).isEqualTo(2);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void chainedThrowAssertion_noMatch() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;

            public class Test {
              public void f(Exception e) {
                assertThat(e).hasMessageThat().isEqualTo("foo");
              }
            }
            """)
        .doTest();
  }

  @Test
  public void clazz() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;

            public class Test {
              public void f(Class<InterruptedException> a, Class<? extends Throwable> b) {
                try {
                } catch (Exception e) {
                  assertThat(e.getCause().getClass()).isEqualTo(IllegalArgumentException.class);
                }
              }
            }
            """)
        .doTest();
  }

  @Test
  public void containment() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;

            public class Test {
              public void f(Iterable<Long> xs, String x) {
                // BUG: Diagnostic contains:
                assertThat(xs).contains(x);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void containment_noMatch() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;

            public class Test {
              public void f(Iterable<Long> xs, Number x) {
                assertThat(xs).contains(x);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void vectorContainment() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import com.google.common.collect.ImmutableList;

            public class Test {
              public void f(Iterable<Long> xs, String x) {
                // BUG: Diagnostic contains:
                assertThat(xs).containsExactlyElementsIn(ImmutableList.of(x));
              }
            }
            """)
        .doTest();
  }

  @Test
  public void vectorContainment_noMatch() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import com.google.common.collect.ImmutableList;

            public class Test {
              public void f(Iterable<Long> xs, Number x) {
                assertThat(xs).containsExactlyElementsIn(ImmutableList.of(x));
              }
            }
            """)
        .doTest();
  }

  @Test
  public void variadicCall_noMatch() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import com.google.common.collect.ImmutableList;

            public class Test {
              public void f(Iterable<Long> xs, Number... x) {
                assertThat(xs).containsExactly((Object[]) x);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void variadicCall_notActuallyAnArray_noMatch() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import com.google.common.collect.ImmutableList;

            public class Test {
              public void f(Iterable<Long> xs, Object x) {
                assertThat(xs).containsExactlyElementsIn((Object[]) x);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void variadicCall_checked() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import com.google.common.collect.ImmutableList;

            public class Test {
              public void f(Iterable<Long> xs, String... x) {
                // BUG: Diagnostic contains:
                assertThat(xs).containsExactly(x);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void variadicCall_primitiveArray() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import com.google.common.collect.ImmutableList;

            public class Test {
              public void f(Iterable<byte[]> xs, byte[] ys) {
                assertThat(xs).containsExactly(ys);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void containsExactlyElementsIn_withArray_match() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import com.google.common.collect.ImmutableList;

            public class Test {
              public void f(Iterable<String> xs, Object... x) {
                assertThat(xs).containsExactlyElementsIn(x);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void containsExactlyElementsIn_withArray_mismatched() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import com.google.common.collect.ImmutableList;

            public class Test {
              public void f(Iterable<Long> xs, String... x) {
                // BUG: Diagnostic contains:
                assertThat(xs).containsExactlyElementsIn(x);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void containsExactlyElementsIn_numericTypes_notSpecialCased() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import com.google.common.collect.ImmutableList;

            public class Test {
              public void f(Iterable<Long> xs, ImmutableList<Integer> ys) {
                // BUG: Diagnostic contains:
                assertThat(xs).containsExactlyElementsIn(ys);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void comparingElementsUsingRawCorrespondence_noFinding() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import com.google.common.collect.ImmutableList;
            import com.google.common.truth.Correspondence;

            public class Test {
              @SuppressWarnings("unchecked")
              public void f(Iterable<Long> xs, Correspondence c) {
                assertThat(xs).comparingElementsUsing(c).doesNotContain("");
              }
            }
            """)
        .doTest();
  }

  @Test
  public void comparingElementsUsing_typeMismatch() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import com.google.common.collect.ImmutableList;
            import com.google.common.truth.Correspondence;

            public class Test {
              public void f(Iterable<Long> xs, Correspondence<Integer, String> c) {
                // BUG: Diagnostic contains:
                assertThat(xs).comparingElementsUsing(c).doesNotContain("");
              }
            }
            """)
        .doTest();
  }

  @Test
  public void comparingElementsUsing_typesMatch() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import com.google.common.collect.ImmutableList;
            import com.google.common.truth.Correspondence;

            public class Test {
              public void f(Iterable<Long> xs, Correspondence<Long, String> c) {
                assertThat(xs).comparingElementsUsing(c).doesNotContain("");
              }
            }
            """)
        .doTest();
  }

  @Test
  public void mapContainsExactlyEntriesIn_keyTypesDiffer() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import java.util.Map;

            public class Test {
              public void f(Map<String, Long> xs, Map<Long, Long> ys) {
                // BUG: Diagnostic contains:
                assertThat(xs).containsExactlyEntriesIn(ys);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void mapContainsExactlyEntriesIn_valueTypesDiffer() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import java.util.Map;

            public class Test {
              public void f(Map<String, Long> xs, Map<String, String> ys) {
                // BUG: Diagnostic contains:
                assertThat(xs).containsExactlyEntriesIn(ys);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void mapContainsExactly() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import java.util.Map;

            public class Test {
              public void f(Map<String, Long> xs) {
                // BUG: Diagnostic contains:
                assertThat(xs).containsExactly("", 1L, "foo", 2L, "bar", 3);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void mapContainsExactly_varargs() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import java.util.Map;

            public class Test {
              public void f(Map<String, Long> xs, String a, Long b, Object... rest) {
                assertThat(xs).containsExactly(a, b, rest);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void multimapContainsExactly() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import com.google.common.collect.Multimap;

            public class Test {
              public void f(Multimap<String, Long> xs) {
                // BUG: Diagnostic contains:
                assertThat(xs).containsExactly("", 1L, "foo", 2L, "bar", 3);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void streamContainsExactly() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import com.google.common.collect.Multimap;
            import java.util.stream.Stream;

            public class Test {
              public void f(Stream<String> xs) {
                // BUG: Diagnostic contains:
                assertThat(xs).containsExactly(1, 2);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void protoTruth_positiveCase() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;

            final class Test {
              void test(TestFieldProtoMessage a, TestProtoMessage b) {
                // BUG: Diagnostic contains:
                assertThat(a).isNotEqualTo(b);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void protoTruth_withModifiers_positiveCase() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;

            final class Test {
              void test(TestFieldProtoMessage a, TestProtoMessage b) {
                // BUG: Diagnostic contains:
                assertThat(a).ignoringFields(1).isNotEqualTo(b);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void protoTruth_contains() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;

            final class Test {
              void test(Iterable<TestFieldProtoMessage> a, TestProtoMessage b) {
                // BUG: Diagnostic contains:
                assertThat(a).containsExactly(b);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void protoTruth_negativeCase() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
            import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;

            final class Test {
              void test(TestFieldProtoMessage a, TestFieldProtoMessage b) {
                assertThat(a).isNotEqualTo(b);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void protoTruth_comparingElementsUsinng() {
    compilationHelper
        .addSourceLines(
            "Test.java",
"""
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;
import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;
import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;

public class Test {
  public void f(
      Iterable<TestProtoMessage> xs, Correspondence<TestFieldProtoMessage, TestProtoMessage> c) {
    // BUG: Diagnostic contains:
    assertThat(xs).comparingElementsUsing(c).doesNotContain(TestProtoMessage.getDefaultInstance());
  }
}
""")
        .doTest();
  }

  @Test
  public void comparingElementsUsingRawCollection_noFinding() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import com.google.common.collect.ImmutableList;
            import com.google.common.truth.Correspondence;

            public class Test {
              @SuppressWarnings("unchecked")
              public void f(Iterable xs, Correspondence<Long, String> c) {
                assertThat(xs).comparingElementsUsing(c).doesNotContain("");
              }
            }
            """)
        .doTest();
  }

  @Test
  public void casts() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;

            public class Test {
              public void f(int a, long b, long c) {
                assertThat((long) a).isAnyOf(b, c);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void multiset_hasCount_match() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import com.google.common.collect.Multiset;

            public class Test {
              public void f(Multiset<String> a, String b) {
                assertThat(a).hasCount(b, 1);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void multiset_hasCount_mismatch() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import static com.google.common.truth.Truth.assertThat;
            import com.google.common.collect.Multiset;

            public class Test {
              public void f(Multiset<String> a, Long b) {
                // BUG: Diagnostic contains:
                assertThat(a).hasCount(b, 1);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void subjectExhaustiveness(
      @TestParameter(valuesProvider = SubjectMethods.class) Method method) {
    // TODO(ghm): isNotSameInstanceAs might be worth flagging, but the check can be even stricter.
    assume().that(method.getName()).isNoneOf("isSameInstanceAs", "isNotSameInstanceAs");

    compilationHelper
        .addSourceLines(
            "Test.java",
            "import static com.google.common.truth.Truth.assertThat;",
            "import com.google.common.collect.ImmutableList;",
            "class Test {",
            "  public void test(String a, Long b) {",
            "    // BUG: Diagnostic contains:",
            getOffensiveLine(method),
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void iterableSubjectExhaustiveness(
      @TestParameter(valuesProvider = IterableSubjectMethods.class) Method method) {
    compilationHelper
        .addSourceLines(
            "Test.java",
            "import static com.google.common.truth.Truth.assertThat;",
            "import com.google.common.collect.ImmutableList;",
            "class Test {",
            "  public void test(Iterable<String> a, Long b) {",
            "    // BUG: Diagnostic contains:",
            getOffensiveLine(method),
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void protoTruthSubject_exhaustive(
      @TestParameter(valuesProvider = ProtoTruthSubjectMethods.class) Method method) {
    compilationHelper
        .addSourceLines(
            "Test.java",
            "import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;",
            "import com.google.errorprone.bugpatterns.proto.ProtoTest.TestFieldProtoMessage;",
            "import com.google.errorprone.bugpatterns.proto.ProtoTest.TestProtoMessage;",
            "public class Test {",
            "  public void f(TestProtoMessage a, TestFieldProtoMessage b) {",
            "    // BUG: Diagnostic contains:",
            getOffensiveLine(method),
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void protoTruthIterableSubjectExhaustiveness() {
    // There are no additional methods on IterableOfProtosSubject, otherwise we'd want to test that.
    // This test just makes sure that remains true.
    assertThat(getAssertionMethods(IterableOfProtosSubject.class)).isEmpty();
  }

  private static String getOffensiveLine(Method method) {
    if (stream(method.getParameterTypes()).allMatch(p -> p.equals(Iterable.class))) {
      return format("    assertThat(a).%s(ImmutableList.of(b));", method.getName());
    } else if (stream(method.getParameterTypes()).allMatch(p -> p.equals(Object.class))) {
      return format("    assertThat(a).%s(b);", method.getName());
    } else if (stream(method.getParameterTypes()).allMatch(p -> p.isArray())) {
      return format("    assertThat(a).%s(new Long[]{b, b, b});", method.getName());
    } else if (stream(method.getParameterTypes())
        .allMatch(p -> p.equals(Object.class) || p.isArray())) {
      return format("    assertThat(a).%s(b, b, b);", method.getName());
    } else if (stream(method.getParameterTypes()).allMatch(Class::isArray)) {
      return format("    assertThat(a).%s(b);", method.getName());
    } else {
      throw new AssertionError();
    }
  }

  private static final class SubjectMethods extends TestParameterValuesProvider {
    @Override
    public ImmutableList<Method> provideValues(Context context) {
      return getAssertionMethods(Subject.class);
    }
  }

  private static final class IterableSubjectMethods extends TestParameterValuesProvider {
    @Override
    public ImmutableList<Method> provideValues(Context context) {
      return getAssertionMethods(IterableSubject.class);
    }
  }

  private static final class ProtoTruthSubjectMethods extends TestParameterValuesProvider {
    @Override
    public ImmutableList<Method> provideValues(Context context) {
      return getAssertionMethods(ProtoSubject.class);
    }
  }

  private static ImmutableList<Method> getAssertionMethods(Class<?> clazz) {
    return stream(clazz.getDeclaredMethods())
        .filter(
            m ->
                Modifier.isPublic(m.getModifiers())
                    && !m.getName().equals("equals")
                    && m.getParameterCount() > 0
                    && !m.getName().startsWith("ignoring")
                    // Kotlin `internal` methods are `public` in the class file.
                    // We can identify them by looking for a mangled name.
                    && !m.getName().contains("$")
                    && (stream(m.getParameterTypes()).allMatch(p -> p.equals(Iterable.class))
                        || stream(m.getParameterTypes())
                            .allMatch(p -> p.equals(Object.class) || p.isArray())
                        || stream(m.getParameterTypes()).allMatch(Class::isArray)))
        .collect(toImmutableList());
  }
}
