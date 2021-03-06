/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.core.container.storage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.sonarsource.sonarlint.core.util.ReversePathTree;

import static org.assertj.core.api.Assertions.assertThat;

public class FileMatcherTest {
  private FileMatcher fileMatcher = new FileMatcher(new ReversePathTree());

  @Test
  public void simple_case_without_prefixes() {
    List<Path> paths = Collections.singletonList(Paths.get("project1/src/main/java/File.java"));
    FileMatcher.Result match = fileMatcher.match(paths, paths);
    assertThat(match.mostCommonLocalPrefix()).isEqualTo(Paths.get(""));
    assertThat(match.mostCommonSqPrefix()).isEqualTo(Paths.get(""));
  }

  @Test
  public void simple_case_with_prefixes() {
    List<Path> localPaths = Collections.singletonList(Paths.get("local/src/main/java/File.java"));
    List<Path> sqPaths = Collections.singletonList(Paths.get("sq/src/main/java/File.java"));
    FileMatcher.Result match = fileMatcher.match(sqPaths, localPaths);
    assertThat(match.mostCommonLocalPrefix()).isEqualTo(Paths.get("local"));
    assertThat(match.mostCommonSqPrefix()).isEqualTo(Paths.get("sq"));
  }

  @Test
  public void should_return_shortest_prefix_if_there_are_ties() {
    List<Path> localPaths = Arrays.asList(
      Paths.get("pom.xml")
    );

    List<Path> sqPaths = Arrays.asList(
      Paths.get("sq1/module2/pom.xml"),
      Paths.get("pom.xml"),
      Paths.get("sq1/module1/pom.xml")
    );
    FileMatcher.Result match = fileMatcher.match(sqPaths, localPaths);
    assertThat(match.mostCommonLocalPrefix()).isEqualTo(Paths.get(""));
    assertThat(match.mostCommonSqPrefix()).isEqualTo(Paths.get(""));
  }
  @Test
  public void should_return_most_common_prefixes() {
    List<Path> localPaths = Arrays.asList(
      Paths.get("local1/src/main/java/A.java"),
      Paths.get("local1/src/main/java/B.java"),
      Paths.get("local2/src/main/java/B.java")
    );

    List<Path> sqPaths = Arrays.asList(
      Paths.get("sq1/src/main/java/A.java"),
      Paths.get("sq2/src/main/java/A.java"),
      Paths.get("sq1/src/main/java/B.java")

    );
    FileMatcher.Result match = fileMatcher.match(sqPaths, localPaths);
    assertThat(match.mostCommonLocalPrefix()).isEqualTo(Paths.get("local1"));
    assertThat(match.mostCommonSqPrefix()).isEqualTo(Paths.get("sq1"));
  }
}
