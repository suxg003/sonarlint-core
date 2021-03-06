/*
 * SonarLint Core - ITs - Tests
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
package its;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class StandaloneTest {

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static StandaloneSonarLintEngineImpl sonarlint;
  private static List<String> logs;
  private File baseDir;

  @BeforeClass
  public static void prepare() throws Exception {
    Path sonarlintUserHome = temp.newFolder().toPath();
    logs = new ArrayList<>();
    Map<String, String> globalProps = new HashMap<>();
    globalProps.put("sonar.global.label", "It works");
    StandaloneGlobalConfiguration config = StandaloneGlobalConfiguration.builder()
      .addPlugin(new File("../plugins/global-extension-plugin/target/global-extension-plugin.jar").toURI().toURL())
      .setSonarLintUserHome(sonarlintUserHome)
      .setLogOutput((msg, level) -> logs.add(msg))
      .setExtraProperties(globalProps)
      .build();
    sonarlint = new StandaloneSonarLintEngineImpl(config);

    assertThat(logs).containsOnlyOnce("Start Global Extension It works");
  }

  @AfterClass
  public static void stop() {
    sonarlint.stop();
    assertThat(logs).containsOnlyOnce("Stop Global Extension");
  }

  @Before
  public void prepareBasedir() throws Exception {
    baseDir = temp.newFolder();
  }

  @Test
  public void globalExtension() throws Exception {
    ClientInputFile inputFile = prepareInputFile("foo.glob", "foo", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.of()), issue -> issues.add(issue), null, null);
    assertThat(issues).extracting("ruleKey", "inputFile.path", "message").containsOnly(
      tuple("global:inc", inputFile.getPath(), "Issue number 0"));

    issues.clear();
    sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.of()), issue -> issues.add(issue), null, null);
    assertThat(issues).extracting("ruleKey", "inputFile.path", "message").containsOnly(
      tuple("global:inc", inputFile.getPath(), "Issue number 1"));
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest, Charset encoding, @Nullable String language) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content, encoding);
    ClientInputFile inputFile = new TestClientInputFile(baseDir.toPath(), file.toPath(), isTest, encoding);
    return inputFile;
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    return prepareInputFile(relativePath, content, isTest, StandardCharsets.UTF_8, null);
  }

}
