/*
 * Copyright 2013-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.java;

import com.facebook.buck.event.ThrowableLogEvent;
import com.facebook.buck.java.classes.ClasspathTraversal;
import com.facebook.buck.java.classes.DefaultClasspathTraverser;
import com.facebook.buck.java.classes.FileLike;
import com.facebook.buck.java.classes.FileLikes;
import com.facebook.buck.step.AbstractExecutionStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

/**
 * {@link Step} that takes a directory or zip of {@code .class} files and traverses it to get the
 * total set of {@code .class} files included by the directory or zip.
 */
public class AccumulateClassNamesStep extends AbstractExecutionStep
    implements Supplier<ImmutableSortedMap<String, HashCode>> {

  /**
   * In the generated {@code classes.txt} file, each line will contain the path to a {@code .class}
   * file (without its suffix) and the SHA-1 hash of its contents, separated by this separator.
   */
  static final String CLASS_NAME_HASH_CODE_SEPARATOR = " ";

  private final Path pathToJarOrClassesDirectory;
  private final Path whereClassNamesShouldBeWritten;

  /**
   * Map of names of {@code .class} files to hashes of their contents.
   * <p>
   * This is not set until this step is executed.
   */
  @Nullable
  private ImmutableSortedMap<String, HashCode> classNames;

  public AccumulateClassNamesStep(Path pathToJarOrClassesDirectory,
      Path whereClassNamesShouldBeWritten) {
    super("get_class_names " + pathToJarOrClassesDirectory + " > " + whereClassNamesShouldBeWritten);
    this.pathToJarOrClassesDirectory = Preconditions.checkNotNull(pathToJarOrClassesDirectory);
    this.whereClassNamesShouldBeWritten = Preconditions.checkNotNull(whereClassNamesShouldBeWritten);
  }

  @Override
  public int execute(ExecutionContext context) {
    final ImmutableSortedMap.Builder<String, HashCode> classNamesBuilder =
        ImmutableSortedMap.naturalOrder();
    Path path = context.getProjectFilesystem().resolve(pathToJarOrClassesDirectory);
    ClasspathTraversal traversal = new ClasspathTraversal(Collections.singleton(path)) {
      @Override
      public void visit(final FileLike fileLike) throws IOException {
        // When traversing a JAR file, it may have resources or directory entries that do not end
        // in .class, which should be ignored.
        if (!FileLikes.isClassFile(fileLike)) {
          return;
        }

        String key = FileLikes.getFileNameWithoutClassSuffix(fileLike);
        InputSupplier<InputStream> input = new InputSupplier<InputStream>() {
          @Override
          public InputStream getInput() throws IOException {
            return fileLike.getInput();
          }
        };
        HashCode value = ByteStreams.hash(input, Hashing.sha1());
        classNamesBuilder.put(key, value);
      }
    };

    try {
      new DefaultClasspathTraverser().traverse(traversal);
    } catch (IOException e) {
      context.logError(e, "Error accumulating class names for %s.", pathToJarOrClassesDirectory);
      return 1;
    }

    ImmutableSortedMap<String, HashCode> classNames = classNamesBuilder.build();
    try {
      context.getProjectFilesystem().writeLinesToPath(
          Iterables.transform(classNames.entrySet(),
              new Function<Map.Entry<String, HashCode>, String>() {
            @Override
            public String apply(Entry<String, HashCode> entry) {
              return entry.getKey() + CLASS_NAME_HASH_CODE_SEPARATOR + entry.getValue();
            }
          }),
          whereClassNamesShouldBeWritten);
    } catch (IOException e) {
      context.getBuckEventBus().post(ThrowableLogEvent.create(e,
          "There was an error writing the list of .class files to %s.",
          whereClassNamesShouldBeWritten));
      return 1;
    }

    this.classNames = classNames;
    return 0;
  }

  @Override
  public ImmutableSortedMap<String, HashCode> get() {
    Preconditions.checkNotNull(classNames,
        "Step must be executed successfully before invoking this method.");
    return classNames;
  }

  @VisibleForTesting
  void setClassNamesForTesting(ImmutableSortedMap<String, HashCode> classNames) {
    this.classNames = classNames;
  }
}
