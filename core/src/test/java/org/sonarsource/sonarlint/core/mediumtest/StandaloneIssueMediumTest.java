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
package org.sonarsource.sonarlint.core.mediumtest;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SystemUtils;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.TestClientInputFile;
import org.sonarsource.sonarlint.core.TestUtils;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.util.PluginLocator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

public class StandaloneIssueMediumTest {

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static StandaloneSonarLintEngineImpl sonarlint;
  private File baseDir;
  private static boolean commercialEnabled;

  @BeforeClass
  public static void prepare() throws Exception {
    Path sonarlintUserHome = temp.newFolder().toPath();

    Path fakeTypeScriptProjectPath = temp.newFolder().toPath();
    Path packagejson = fakeTypeScriptProjectPath.resolve("package.json");
    FileUtils.write(packagejson.toFile(), "{"
      + "\"devDependencies\": {\n" +
      "    \"typescript\": \"2.6.1\"\n" +
      "  }"
      + "}", StandardCharsets.UTF_8);
    ProcessBuilder pb = new ProcessBuilder("npm" + (SystemUtils.IS_OS_WINDOWS ? ".cmd" : ""), "install")
      .directory(fakeTypeScriptProjectPath.toFile())
      .inheritIO();
    Process process = pb.start();
    if (process.waitFor() != 0) {
      fail("Unable to run npm install");
    }

    Map<String, String> extraProperties = new HashMap<>();
    extraProperties.put("sonar.typescript.internal.typescriptLocation", fakeTypeScriptProjectPath.resolve("node_modules").toString());
    StandaloneGlobalConfiguration.Builder configBuilder = StandaloneGlobalConfiguration.builder()
      .addPlugin(PluginLocator.getJavaScriptPluginUrl())
      .addPlugin(PluginLocator.getJavaPluginUrl())
      .addPlugin(PluginLocator.getPhpPluginUrl())
      .addPlugin(PluginLocator.getPythonPluginUrl())
      .addPlugin(PluginLocator.getXooPluginUrl())
      .addPlugin(PluginLocator.getTypeScriptPluginUrl())
      .setSonarLintUserHome(sonarlintUserHome)
      .setLogOutput((msg, level) -> System.out.println(msg))
      .setExtraProperties(extraProperties);

    // commercial plugins might not be available (if you pass -Dcommercial to maven, a profile will be activated that downloads the
    // commercial plugins)
    if (System.getProperty("commercial") != null) {
      commercialEnabled = true;
      configBuilder.addPlugin(PluginLocator.getCppPluginUrl());
      configBuilder.addPlugin(PluginLocator.getLicensePluginUrl());
    } else {
      commercialEnabled = false;
    }
    sonarlint = new StandaloneSonarLintEngineImpl(configBuilder.build());
  }

  @AfterClass
  public static void stop() {
    sonarlint.stop();
  }

  @Before
  public void prepareBasedir() throws Exception {
    baseDir = temp.newFolder();
  }

  @Test
  public void simpleJavaScript() throws Exception {

    RuleDetails ruleDetails = sonarlint.getRuleDetails("javascript:UnusedVariable");
    assertThat(ruleDetails.getName()).isEqualTo("Unused local variables and functions should be removed");
    assertThat(ruleDetails.getLanguage()).isEqualTo("js");
    assertThat(ruleDetails.getSeverity()).isEqualTo("MINOR");
    assertThat(ruleDetails.getTags()).containsOnly("unused");
    assertThat(ruleDetails.getHtmlDescription()).contains("<p>", "If a local variable or a local function is declared but not used");

    String content = "function foo() {\n"
      + "  var x;\n"
      + "  var y; //NOSONAR\n"
      + "}";
    ClientInputFile inputFile = prepareInputFile("foo.js", content, false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Collections.singletonList(inputFile), ImmutableMap.of()), issues::add, null,
      null);
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("javascript:UnusedVariable", 2, inputFile.getPath()));

    // SLCORE-160
    inputFile = prepareInputFile("node_modules/foo.js", content, false);

    issues.clear();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Collections.singletonList(inputFile), ImmutableMap.of()), issues::add, null,
      null);
    assertThat(issues).isEmpty();
  }

  @Test
  public void simpleTypeScript() throws Exception {
    // TODO enable it again once https://github.com/SonarSource/SonarTS/issues/598 is fixed
    Assume.assumeFalse(SystemUtils.IS_OS_WINDOWS);

    RuleDetails ruleDetails = sonarlint.getRuleDetails("typescript:S1764");
    assertThat(ruleDetails.getName()).isEqualTo("Identical expressions should not be used on both sides of a binary operator");
    assertThat(ruleDetails.getLanguage()).isEqualTo("ts");
    assertThat(ruleDetails.getSeverity()).isEqualTo("MAJOR");
    assertThat(ruleDetails.getTags()).containsOnly("cert");
    assertThat(ruleDetails.getHtmlDescription()).contains("<p>", "Using the same value on either side of a binary operator is almost always a mistake");

    final File tsConfig = new File(baseDir, "tsconfig.json");
    FileUtils.write(tsConfig, "{}", StandardCharsets.UTF_8);

    ClientInputFile inputFile = prepareInputFile("foo.ts", "function foo() {\n"
      + "  if(bar() && bar()) { return 42; }\n"
      + "}", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Collections.singletonList(inputFile), ImmutableMap.of()), issues::add, null,
      null);
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("typescript:S1764", 2, inputFile.getPath()));

  }

  @Test
  public void fileEncoding() throws IOException {
    ClientInputFile inputFile = prepareInputFile("foo.xoo", "function xoo() {\n"
      + "  var xoo1, xoo2;\n"
      + "  var xoo; //NOSONAR\n"
      + "}", false, StandardCharsets.UTF_16, null);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Collections.singletonList(inputFile), ImmutableMap.of()), issues::add, null, null);
    assertThat(issues).extracting("ruleKey", "startLine", "startLineOffset", "inputFile.path").containsOnly(
      tuple("xoo:HasTag", 1, 9, inputFile.getPath()),
      tuple("xoo:HasTag", 2, 6, inputFile.getPath()),
      tuple("xoo:HasTag", 2, 12, inputFile.getPath()));
  }

  @Test
  public void simpleXoo() throws Exception {
    ClientInputFile inputFile = prepareInputFile("foo.xoo", "function xoo() {\n"
      + "  var xoo1, xoo2;\n"
      + "  var xoo; //NOSONAR\n"
      + "}", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Collections.singletonList(inputFile), ImmutableMap.of()), issues::add, null, null);
    assertThat(issues).extracting("ruleKey", "startLine", "startLineOffset", "inputFile.path").containsOnly(
      tuple("xoo:HasTag", 1, 9, inputFile.getPath()),
      tuple("xoo:HasTag", 2, 6, inputFile.getPath()),
      tuple("xoo:HasTag", 2, 12, inputFile.getPath()));
  }

  @Test
  public void simpleCpp() throws Exception {
    assumeTrue(commercialEnabled);
    ClientInputFile inputFile = prepareInputFile("foo.cpp", "void fun() {\n "
      + "  int a = 0; \n"
      + "  if (a) {fun();}\n"
      + "  if (a) {fun();} // NOSONAR\n"
      + "}\n", false, StandardCharsets.UTF_8, "cpp");

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Collections.singletonList(inputFile),
        ImmutableMap.of("sonar.cfamily.build-wrapper-output.bypass", "true")),
      issues::add, null, null);
    assertThat(issues).extracting("ruleKey", "startLine", "startLineOffset", "inputFile.path").containsOnly(
      tuple("cpp:S2583", 3, 6, inputFile.getPath()));
  }

  @Test
  public void analysisErrors() throws Exception {
    ClientInputFile inputFile = prepareInputFile("foo.xoo", "function foo() {\n"
      + "  var xoo;\n"
      + "  var y; //NOSONAR\n"
      + "}", false);
    prepareInputFile("foo.xoo.error", "1,2,error analysing\n2,3,error analysing", false);

    final List<Issue> issues = new ArrayList<>();
    AnalysisResults results = sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Collections.singletonList(inputFile), ImmutableMap.of()), issues::add, null, null);
    assertThat(results.failedAnalysisFiles()).containsExactly(inputFile);
    assertThat(issues).extracting("ruleKey", "startLine", "startLineOffset", "inputFile.path").containsOnly(
      tuple("xoo:HasTag", 2, 6, inputFile.getPath()));
  }

  @Test
  public void returnLanguagePerFile() throws IOException {
    ClientInputFile inputFile = prepareInputFile("foo.xoo", "function foo() {\n"
      + "  var xoo;\n"
      + "  var y; //NOSONAR\n"
      + "}", false);

    final List<Issue> issues = new ArrayList<>();
    AnalysisResults results = sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Collections.singletonList(inputFile), ImmutableMap.of()), issues::add, null, null);
    assertThat(results.languagePerFile()).containsExactly(entry(inputFile, "xoo"));
  }

  @Test
  public void simplePhp() throws Exception {

    ClientInputFile inputFile = prepareInputFile("foo.php", "<?php\n"
      + "function writeMsg($fname) {\n"
      + "    $i = 0; // NOSONAR\n"
      + "    echo \"Hello world!\";\n"
      + "}\n"
      + "?>", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Collections.singletonList(inputFile), ImmutableMap.of()), issues::add,
      null, null);
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("php:S1172", 2, inputFile.getPath()));
  }

  @Test
  public void simplePython() throws Exception {

    ClientInputFile inputFile = prepareInputFile("foo.py", "def my_function(name):\n"
      + "    print \"Hello\"\n"
      + "    print \"world!\" # NOSONAR\n"
      + "\n", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Collections.singletonList(inputFile), ImmutableMap.of()), issues::add,
      null, null);
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("python:PrintStatementUsage", 2, inputFile.getPath()));
  }

  // SLCORE-162
  @Test
  public void useRelativePathToEvaluatePathPatterns() throws Exception {

    final File file = new File(baseDir, "foo.tmp"); // Temporary file doesn't have the correct file suffix
    FileUtils.write(file, "def my_function(name):\n"
      + "    print \"Hello\"\n"
      + "    print \"world!\" # NOSONAR\n"
      + "\n", StandardCharsets.UTF_8);
    ClientInputFile inputFile = new TestClientInputFile(file.toPath(), "foo.py", false, StandardCharsets.UTF_8, null);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Collections.singletonList(inputFile), ImmutableMap.of()), issues::add,
      null, null);
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("python:PrintStatementUsage", 2, inputFile.getPath()));
  }

  @Test
  public void simpleJava() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Collections.singletonList(inputFile), ImmutableMap.of()), issues::add,
      null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile.getPath(), "MINOR"),
      tuple("squid:S106", 4, inputFile.getPath(), "MAJOR"));
  }

  @Test
  public void simpleJavaNoHotspots() throws Exception {
    assertThat(sonarlint.getAllRuleDetails()).extracting(RuleDetails::getKey).doesNotContain("squid:S1313");
    assertThat(sonarlint.getRuleDetails("squid:S1313")).isNull();

    ClientInputFile inputFile = prepareInputFile("foo/Foo.java",
      "package foo;\n"
        + "public class Foo {\n"
        + "  String ip = \"192.168.12.42\"; // Hotspots should not be reported in SonarLint\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), singletonList(inputFile), ImmutableMap.of(), emptyList(),
        singleton(new RuleKey("squid", "S1313"))),
      issues::add,
      null, null);

    assertThat(issues).isEmpty();
  }

  @Test
  public void simpleJavaPomXml() throws Exception {
    ClientInputFile inputFile = prepareInputFile("pom.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<project>\n"
        + "  <modelVersion>4.0.0</modelVersion>\n"
        + "  <groupId>com.foo</groupId>\n"
        + "  <artifactId>bar</artifactId>\n"
        + "  <version>${pom.version}</version>\n"
        + "</project>",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Collections.singletonList(inputFile), ImmutableMap.of()), issues::add,
      null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S3421", 6, inputFile.getPath(), "MINOR"));
  }

  @Test
  public void supportJavaSuppressWarning() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  @SuppressWarnings(\"squid:S106\")\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Collections.singletonList(inputFile), ImmutableMap.of()), issues::add,
      null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("squid:S1481", 4, inputFile.getPath(), "MINOR"));
  }

  @Test
  public void simpleJavaWithBytecode() throws Exception {
    ClientInputFile inputFile = TestUtils.createInputFile(new File("src/test/projects/java-with-bytecode/src/Foo.java").getAbsoluteFile().toPath(), "src/Foo.java", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Collections.singletonList(inputFile),
      ImmutableMap.of("sonar.java.binaries", new File("src/test/projects/java-with-bytecode/bin").getAbsolutePath())),
      issues::add, null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("squid:S106", 5, inputFile.getPath()),
      tuple("squid:S1220", null, inputFile.getPath()),
      tuple("squid:UnusedPrivateMethod", 8, inputFile.getPath()),
      tuple("squid:S1186", 8, inputFile.getPath()));
  }

  @Test
  public void simpleJavaWithExcludedRules() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}",
      false);

    final Collection<RuleKey> excludedRules = singleton(new RuleKey("squid", "S106"));
    final Collection<RuleKey> includedRules = emptyList();
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Collections.singletonList(inputFile), ImmutableMap.of(), excludedRules, includedRules),
      issues::add, null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile.getPath(), "MINOR"));
  }

  @Test
  public void simpleJavaWithIncludedRules() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "import java.util.Optional;\n"
        + "public class Foo {\n"
        + "  public void foo(Optional<String> name) {  // for squid:3553, not in Sonar Way\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\" + name.isPresent());\n"
        + "  }\n"
        + "}",
      false);

    final Collection<RuleKey> excludedRules = emptyList();
    final Collection<RuleKey> includedRules = singleton(new RuleKey("squid", "S3553"));
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), singletonList(inputFile), ImmutableMap.of(), excludedRules, includedRules),
      issues::add, null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S3553", 3, inputFile.getPath(), "MAJOR"),
      tuple("squid:S106", 5, inputFile.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("squid:S1481", 4, inputFile.getPath(), "MINOR"));
  }

  @Test
  public void simpleJavaWithIncludedAndExcludedRules() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "import java.util.Optional;\n"
        + "public class Foo {\n"
        + "  public void foo(Optional<String> name) {  // for squid:3553, not in Sonar Way\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\" + name.isPresent());\n"
        + "  }\n"
        + "}",
      false);

    // exclusion wins
    final Collection<RuleKey> excludedRules = Collections.singleton(new RuleKey("squid", "S3553"));
    final Collection<RuleKey> includedRules = Collections.singleton(new RuleKey("squid", "S3553"));
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Collections.singletonList(inputFile), ImmutableMap.of(), excludedRules, includedRules),
      issues::add, null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S106", 5, inputFile.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("squid:S1481", 4, inputFile.getPath(), "MINOR"));
  }

  @Test
  public void testJavaSurefireDontCrashAnalysis() throws Exception {

    File surefireReport = new File(baseDir, "reports/TEST-FooTest.xml");
    FileUtils.write(surefireReport, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<testsuite name=\"FooTest\" time=\"0.121\" tests=\"1\" errors=\"0\" skipped=\"0\" failures=\"0\">\n" +
      "<testcase name=\"errorAnalysis\" classname=\"FooTest\" time=\"0.031\"/>\n" +
      "</testsuite>");

    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    ClientInputFile inputFileTest = prepareInputFile("FooTest.java",
      "public class FooTest {\n"
        + "  public void testFoo() {\n"
        + "  }\n"
        + "}",
      true);

    final List<Issue> issues = new ArrayList<>();
    AnalysisResults results = sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile, inputFileTest),
        ImmutableMap.of("sonar.junit.reportsPath", "reports/")),
      issues::add, null, null);

    assertThat(results.indexedFileCount()).isEqualTo(2);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("squid:S106", 4, inputFile.getPath()),
      tuple("squid:S1220", null, inputFile.getPath()),
      tuple("squid:S1481", 3, inputFile.getPath()),
      tuple("squid:S2187", 1, inputFileTest.getPath()));
  }

  @Test
  public void concurrentAnalysis() throws Throwable {
    final ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    final Path workDir = temp.newFolder().toPath();

    int parallelExecutions = 4;

    ExecutorService executor = Executors.newFixedThreadPool(parallelExecutions);

    List<Future<?>> results = new ArrayList<>();
    for (int i = 0; i < parallelExecutions; i++) {

      Runnable worker = () -> sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), workDir,
        Collections.singletonList(inputFile), ImmutableMap.of()), issue -> {
        }, null, null);
      results.add(executor.submit(worker));
    }
    executor.shutdown();

    while (!executor.isTerminated()) {
    }

    for (Future<?> future : results) {
      try {
        future.get();
      } catch (ExecutionException e) {
        throw e.getCause();
      }
    }
  }

  @Test
  public void lazy_init_file_metadata() throws Exception {
    final ClientInputFile inputFile1 = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);
    File unexistingPath = new File(baseDir, "missing.bin");
    assertThat(unexistingPath).doesNotExist();
    ClientInputFile inputFile2 = new TestClientInputFile(unexistingPath.toPath(), "missing.bin", false, StandardCharsets.UTF_8, null);

    final List<Issue> issues = new ArrayList<>();
    final List<String> logs = new ArrayList<>();
    AnalysisResults analysisResults = sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile1, inputFile2), ImmutableMap.of()), issues::add,
      (m, l) -> logs.add(m), null);

    assertThat(analysisResults.failedAnalysisFiles()).isEmpty();
    assertThat(analysisResults.indexedFileCount()).isEqualTo(2);
    assertThat(logs).contains("Initializing metadata of file " + inputFile1.uri());
    assertThat(logs).doesNotContain("Initializing metadata of file " + inputFile2.uri());
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest, Charset encoding, @Nullable String language) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content, encoding);
    return new TestClientInputFile(file.toPath(), relativePath, isTest, encoding, language);
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    return prepareInputFile(relativePath, content, isTest, StandardCharsets.UTF_8, null);
  }

}
