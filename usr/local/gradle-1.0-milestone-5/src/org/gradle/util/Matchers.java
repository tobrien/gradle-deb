/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.util;

import org.gradle.api.Buildable;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.hamcrest.*;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.internal.ReturnDefaultValueAction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertTrue;

public class Matchers {
    @Factory
    public static <T> Matcher<T> reflectionEquals(T equalsTo) {
        return new ReflectionEqualsMatcher<T>(equalsTo);
    }

    @Factory
    public static <T, S extends Iterable<? extends T>> Matcher<S> hasSameItems(final S items) {
        return new BaseMatcher<S>() {
            public boolean matches(Object o) {
                Iterable<? extends T> iterable = (Iterable<? extends T>) o;
                List<T> actual = new ArrayList<T>();
                for (T t : iterable) {
                    actual.add(t);
                }
                List<T> expected = new ArrayList<T>();
                for (T t : items) {
                    expected.add(t);
                }

                return expected.equals(actual);
            }

            public void describeTo(Description description) {
                description.appendText("an Iterable that has same items as ").appendValue(items);
            }
        };
    }

    @Factory
    public static <T extends CharSequence> Matcher<T> matchesRegexp(final String pattern) {
        return new BaseMatcher<T>() {
            public boolean matches(Object o) {
                return Pattern.compile(pattern).matcher((CharSequence) o).matches();
            }

            public void describeTo(Description description) {
                description.appendText("a CharSequence that matches regexp ").appendValue(pattern);
            }
        };
    }

    @Factory
    public static <T extends CharSequence> Matcher<T> matchesRegexp(final Pattern pattern) {
        return new BaseMatcher<T>() {
            public boolean matches(Object o) {
                return pattern.matcher((CharSequence) o).matches();
            }

            public void describeTo(Description description) {
                description.appendText("a CharSequence that matches regexp ").appendValue(pattern);
            }
        };
    }

    @Factory
    public static <T> Matcher<T> strictlyEqual(final T other) {
        return new BaseMatcher<T>() {
            public boolean matches(Object o) {
                return strictlyEquals(o, other);
            }

            public void describeTo(Description description) {
                description.appendText("an Object that strictly equals ").appendValue(other);
            }
        };
    }

    public static boolean strictlyEquals(Object a, Object b) {
        if (!a.equals(b)) {
            return false;
        }
        if (!b.equals(a)) {
            return false;
        }
        if (!a.equals(a)) {
            return false;
        }
        if (b.equals(null)) {
            return false;
        }
        if (b.equals(new Object())) {
            return false;
        }
        if (a.hashCode() != b.hashCode()) {
            return false;
        }
        return true;

    }

    @Factory
    public static Matcher<String> containsLine(final String line) {
        return new BaseMatcher<String>() {
            public boolean matches(Object o) {
                return containsLine(equalTo(line)).matches(o);
            }

            public void describeTo(Description description) {
                description.appendText("a String that contains line ").appendValue(line);
            }
        };
    }

    @Factory
    public static Matcher<String> containsLine(final Matcher<? super String> matcher) {
        return new BaseMatcher<String>() {
            public boolean matches(Object o) {
                String str = (String) o;
                return containsLine(str, matcher);
            }

            public void describeTo(Description description) {
                description.appendText("a String that contains line that is ").appendDescriptionOf(matcher);
            }
        };
    }

    public static boolean containsLine(String action, String expected) {
        return containsLine(action, equalTo(expected));
    }

    public static boolean containsLine(String actual, Matcher<? super String> matcher) {
        BufferedReader reader = new BufferedReader(new StringReader(actual));
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (matcher.matches(line)) {
                    return true;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    @Factory
    public static Matcher<Iterable<?>> isEmpty() {
        return new BaseMatcher<Iterable<?>>() {
            public boolean matches(Object o) {
                Iterable<?> iterable = (Iterable<?>) o;
                return iterable != null && !iterable.iterator().hasNext();
            }

            public void describeTo(Description description) {
                description.appendText("an empty Iterable");
            }
        };
    }

    @Factory
    public static Matcher<Map<?, ?>> isEmptyMap() {
        return new BaseMatcher<Map<?, ?>>() {
            public boolean matches(Object o) {
                Map<?, ?> map = (Map<?, ?>) o;
                return map != null && map.isEmpty();
            }

            public void describeTo(Description description) {
                description.appendText("an empty map");
            }
        };
    }

    @Factory
    public static Matcher<Object[]> isEmptyArray() {
        return new BaseMatcher<Object[]>() {
            public boolean matches(Object o) {
                Object[] array = (Object[]) o;
                return array != null && array.length == 0;
            }

            public void describeTo(Description description) {
                description.appendText("an empty array");
            }
        };
    }

    @Factory
    public static Matcher<Throwable> hasMessage(final Matcher<String> matcher) {
        return new BaseMatcher<Throwable>() {
            public boolean matches(Object o) {
                Throwable t = (Throwable) o;
                return matcher.matches(t.getMessage());
            }

            public void describeTo(Description description) {
                description.appendText("an exception with message that is ").appendDescriptionOf(matcher);
            }
        };
    }

    @Factory
    public static Matcher<Task> dependsOn(final String... tasks) {
        return dependsOn(equalTo(new HashSet<String>(Arrays.asList(tasks))));
    }

    @Factory
    public static Matcher<Task> dependsOn(final Matcher<? extends Iterable<String>> matcher) {
        return new BaseMatcher<Task>() {
            public boolean matches(Object o) {
                Task task = (Task) o;
                Set<String> names = new HashSet<String>();
                Set<? extends Task> depTasks = task.getTaskDependencies().getDependencies(task);
                for (Task depTask : depTasks) {
                    names.add(depTask.getName());
                }
                boolean matches = matcher.matches(names);
                if (!matches) {
                    StringDescription description = new StringDescription();
                    matcher.describeTo(description);
                    System.out.println(String.format("expected %s, got %s.", description.toString(), names));
                }
                return matches;
            }

            public void describeTo(Description description) {
                description.appendText("a Task that depends on ").appendDescriptionOf(matcher);
            }
        };
    }

    @Factory
    public static <T extends Buildable> Matcher<T> builtBy(String... tasks) {
        return builtBy(equalTo(new HashSet<String>(Arrays.asList(tasks))));
    }

    @Factory
    public static <T extends Buildable> Matcher<T> builtBy(final Matcher<? extends Iterable<String>> matcher) {
        return new BaseMatcher<T>() {
            public boolean matches(Object o) {
                Buildable task = (Buildable) o;
                Set<String> names = new HashSet<String>();
                Set<? extends Task> depTasks = task.getBuildDependencies().getDependencies(null);
                for (Task depTask : depTasks) {
                    names.add(depTask.getName());
                }
                boolean matches = matcher.matches(names);
                if (!matches) {
                    StringDescription description = new StringDescription();
                    matcher.describeTo(description);
                    System.out.println(String.format("expected %s, got %s.", description.toString(), names));
                }
                return matches;
            }

            public void describeTo(Description description) {
                description.appendText("a Buildable that is built by ").appendDescriptionOf(matcher);
            }
        };
    }

    @Factory
    public static <T extends FileCollection> Matcher<T> sameCollection(final FileCollection expected) {
        return new BaseMatcher<T>() {
            public boolean matches(Object o) {
                FileCollection actual = (FileCollection) o;
                List<? extends FileCollection> actualCollections = unpack(actual);
                List<? extends FileCollection> expectedCollections = unpack(expected);
                boolean equals = actualCollections.equals(expectedCollections);
                if (!equals) {
                    System.out.println("expected: " + expectedCollections);
                    System.out.println("actual: " + actualCollections);
                }
                return equals;
            }

            private List<? extends FileCollection> unpack(FileCollection expected) {
                if (expected instanceof UnionFileCollection) {
                    UnionFileCollection collection = (UnionFileCollection) expected;
                    return new ArrayList<FileCollection>(collection.getSources());
                }
                if (expected instanceof DefaultConfigurableFileCollection) {
                    DefaultConfigurableFileCollection collection = (DefaultConfigurableFileCollection) expected;
                    return new ArrayList<FileCollection>((Set) collection.getFrom());
                }
                throw new RuntimeException("Cannot get children of " + expected);
            }

            public void describeTo(Description description) {
                description.appendText("same file collection as ").appendValue(expected);
            }
        };
    }

    /**
     * Returns a placeholder for a mock method parameter.
     */
    public static <T> Collector<T> collector() {
        return new Collector<T>();
    }

    /**
     * Returns an action which collects the first parameter into the given placeholder.
     */
    public static CollectAction collectTo(Collector<?> collector) {
        return new CollectAction(collector);
    }

    public static class CollectAction implements Action {
        private Action action = new ReturnDefaultValueAction();
        private final Collector<?> collector;

        public CollectAction(Collector<?> collector) {
            this.collector = collector;
        }

        public Action then(Action action) {
            this.action = action;
            return this;
        }

        public void describeTo(Description description) {
            description.appendText("collect parameter then ").appendDescriptionOf(action);
        }

        public Object invoke(Invocation invocation) throws Throwable {
            collector.setValue(invocation.getParameter(0));
            return action.invoke(invocation);
        }
    }

    public static class Collector<T> {
        private T value;
        private boolean set;

        public T get() {
            assertTrue(set);
            return value;
        }

        void setValue(Object parameter) {
            value = (T) parameter;
            set = true;
        }
    }
}
