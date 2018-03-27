package dotty
package tools
package dotc

import org.junit.{ Test, BeforeClass, AfterClass }
import org.junit.Assert._
import org.junit.Assume._
import org.junit.experimental.categories.Category

import java.nio.file._
import java.util.stream.{ Stream => JStream }
import scala.collection.JavaConverters._
import scala.util.matching.Regex
import scala.concurrent.duration._
import vulpix._
import dotty.tools.io.JFile


class CompilationTests extends ParallelTesting {
  import ParallelTesting._
  import TestConfiguration._
  import CompilationTests._

  // Test suite configuration --------------------------------------------------

  def maxDuration = 30.seconds
  def numberOfSlaves = 5
  def safeMode = Properties.testsSafeMode
  def isInteractive = SummaryReport.isInteractive
  def testFilter = Properties.testsFilter

  // Positive tests ------------------------------------------------------------

  // @Test  // enable to test compileStdLib separately with detailed stats
  def compileStdLibOnly: Unit = {
    implicit val testGroup: TestGroup = TestGroup("compileStdLibOnly")
    compileList("compileStdLib", StdLibSources.whitelisted, scala2Mode.and("-migration", "-Yno-inline", "-Ydetailed-stats"))
  }.checkCompile()

  @Test def compilePos: Unit = {
    implicit val testGroup: TestGroup = TestGroup("compilePos")
    compileList("compileStdLib", StdLibSources.whitelisted, scala2Mode.and("-migration", "-Yno-inline")) +
    compileDir("compiler/src/dotty/tools/dotc/ast", defaultOptions) +
    compileDir("compiler/src/dotty/tools/dotc/config", defaultOptions) +
    compileDir("compiler/src/dotty/tools/dotc/core", defaultOptions) +
    compileDir("compiler/src/dotty/tools/dotc/transform", defaultOptions) +
    compileDir("compiler/src/dotty/tools/dotc/parsing", defaultOptions) +
    compileDir("compiler/src/dotty/tools/dotc/printing", defaultOptions) +
    compileDir("compiler/src/dotty/tools/dotc/reporting", defaultOptions) +
    compileDir("compiler/src/dotty/tools/dotc/typer", defaultOptions) +
    compileDir("compiler/src/dotty/tools/dotc/util", defaultOptions) +
    compileDir("compiler/src/dotty/tools/io", defaultOptions) +
    compileDir("compiler/src/dotty/tools/dotc/core", TestFlags(classPath, noCheckOptions)) +
    compileFile("tests/pos/nullarify.scala", defaultOptions.and("-Ycheck:nullarify")) +
    compileFile("tests/pos-scala2/rewrites.scala", scala2Mode.and("-rewrite")).copyToTarget() +
    compileFile("tests/pos-special/utf8encoded.scala", explicitUTF8) +
    compileFile("tests/pos-special/utf16encoded.scala", explicitUTF16) +
    compileFile("tests/pos-special/i3323.scala", defaultOptions.and("-Xfatal-warnings")) +
    compileFile("tests/pos-special/i3323b.scala", defaultOptions.and("-Xfatal-warnings")) +
    compileFile("tests/pos-special/i3589-b.scala", defaultOptions.and("-Xfatal-warnings")) +
    compileFile("tests/pos-special/i4166.scala", defaultOptions.and("-Xfatal-warnings")) +
    compileFile("tests/pos-special/i4185.scala", defaultOptions.and("-Xfatal-warnings")) +
    compileFile("tests/pos-special/completeFromSource/Test.scala", defaultOptions.and("-sourcepath", "tests/pos-special")) +
    compileFile("tests/pos-special/completeFromSource/Test2.scala", defaultOptions.and("-sourcepath", "tests/pos-special")) +
    compileFile("tests/pos-special/completeFromSource/Test3.scala", defaultOptions.and("-sourcepath", "tests/pos-special", "-scansource")) +
    compileList(
      "compileMixed",
      List(
        "tests/pos/B.scala",
        "scala2-library/src/library/scala/collection/immutable/Seq.scala",
        "scala2-library/src/library/scala/collection/parallel/ParSeq.scala",
        "scala2-library/src/library/scala/package.scala",
        "scala2-library/src/library/scala/collection/GenSeqLike.scala",
        "scala2-library/src/library/scala/collection/SeqLike.scala",
        "scala2-library/src/library/scala/collection/generic/GenSeqFactory.scala"
      ),
      scala2Mode
    ) +
    compileFilesInDir("tests/pos-special/spec-t5545", defaultOptions) +
    compileFilesInDir("tests/pos-special/strawman-collections", defaultOptions) +
    compileFilesInDir("tests/pos-special/isInstanceOf", defaultOptions.and("-Xfatal-warnings")) +
    compileFile("scala2-library/src/library/scala/collection/immutable/IndexedSeq.scala", defaultOptions) +
    compileFile("scala2-library/src/library/scala/collection/parallel/mutable/ParSetLike.scala", defaultOptions) +
    compileList(
      "parSetSubset",
      List(
       "scala2-library/src/library/scala/collection/parallel/mutable/ParSetLike.scala",
       "scala2-library/src/library/scala/collection/parallel/mutable/ParSet.scala",
       "scala2-library/src/library/scala/collection/mutable/SetLike.scala"
      ),
      scala2Mode
    ) +
    // FIXME: This fails with .times(2), see #2799
    compileList(
      "testPredefDeprecatedNonCyclic",
      List(
        "scala2-library/src/library/scala/io/Position.scala",
        "scala2-library/src/library/scala/Predef.scala",
        "scala2-library/src/library/scala/deprecated.scala"
      ),
      scala2Mode
    ) +
    compileFilesInDir("tests/new", defaultOptions) +
    compileFilesInDir("tests/pos-scala2", scala2Mode) +
    compileFilesInDir("tests/pos", defaultOptions) +
    compileFilesInDir("tests/pos-no-optimise", defaultOptions) +
    compileFilesInDir("tests/pos-deep-subtype", allowDeepSubtypes) +
    compileFilesInDir("tests/pos-kind-polymorphism", defaultOptions and "-Ykind-polymorphism") +
    compileDir("tests/pos/i1137-1", defaultOptions and "-Yemit-tasty") +
    compileFile(
      // succeeds despite -Xfatal-warnings because of -nowarn
      "tests/neg-custom-args/fatal-warnings/xfatalWarnings.scala",
      defaultOptions.and("-nowarn", "-Xfatal-warnings")
    )
  }.checkCompile()

  @Test def posTwice: Unit = {
    implicit val testGroup: TestGroup = TestGroup("posTwice")
    compileFile("tests/pos/Labels.scala", defaultOptions) +
    compileFilesInDir("tests/pos-java-interop", defaultOptions) +
    compileFilesInDir("tests/pos-java-interop-separate", defaultOptions) +
    compileFile("tests/pos/t2168.scala", defaultOptions) +
    compileFile("tests/pos/erasure.scala", defaultOptions) +
    compileFile("tests/pos/Coder.scala", defaultOptions) +
    compileFile("tests/pos/blockescapes.scala", defaultOptions) +
    compileFile("tests/pos/collections.scala", defaultOptions) +
    compileFile("tests/pos/functions1.scala", defaultOptions) +
    compileFile("tests/pos/implicits1.scala", defaultOptions) +
    compileFile("tests/pos/inferred.scala", defaultOptions) +
    compileFile("tests/pos/Patterns.scala", defaultOptions) +
    compileFile("tests/pos/selftypes.scala", defaultOptions) +
    compileFile("tests/pos/varargs.scala", defaultOptions) +
    compileFile("tests/pos/vararg-pattern.scala", defaultOptions) +
    compileFile("tests/pos/opassign.scala", defaultOptions) +
    compileFile("tests/pos/typedapply.scala", defaultOptions) +
    compileFile("tests/pos/nameddefaults.scala", defaultOptions) +
    compileFile("tests/pos/desugar.scala", defaultOptions) +
    compileFile("tests/pos/sigs.scala", defaultOptions) +
    compileFile("tests/pos/typers.scala", defaultOptions) +
    compileDir("tests/pos/typedIdents", defaultOptions) +
    compileFile("tests/pos/assignments.scala", defaultOptions) +
    compileFile("tests/pos/packageobject.scala", defaultOptions) +
    compileFile("tests/pos/overloaded.scala", defaultOptions) +
    compileFile("tests/pos/overrides.scala", defaultOptions) +
    compileDir("tests/pos/java-override", defaultOptions) +
    compileFile("tests/pos/templateParents.scala", defaultOptions) +
    compileFile("tests/pos/overloadedAccess.scala", defaultOptions) +
    compileFile("tests/pos/approximateUnion.scala", defaultOptions) +
    compileFilesInDir("tests/pos/tailcall", defaultOptions) +
    compileShallowFilesInDir("tests/pos/pos_valueclasses", defaultOptions) +
    compileFile("tests/pos/subtyping.scala", defaultOptions) +
    compileFile("tests/pos/i0239.scala", defaultOptions) +
    compileFile("tests/pos/anonClassSubtyping.scala", defaultOptions) +
    compileFile("tests/pos/extmethods.scala", defaultOptions) +
    compileFile("tests/pos/companions.scala", defaultOptions) +
    compileList(
      "testNonCyclic",
      List(
        "compiler/src/dotty/tools/dotc/CompilationUnit.scala",
        "compiler/src/dotty/tools/dotc/core/Types.scala",
        "compiler/src/dotty/tools/dotc/ast/Trees.scala"
      ),
      defaultOptions.and("-Xprompt")
    ) +
    compileList(
      "testIssue34",
      List(
        "compiler/src/dotty/tools/dotc/config/Properties.scala",
        "compiler/src/dotty/tools/dotc/config/PathResolver.scala"
      ),
      defaultOptions.and("-Xprompt")
    )
  }.times(2).checkCompile()

  // Negative tests ------------------------------------------------------------

  @Test def compileNeg: Unit = {
    implicit val testGroup: TestGroup = TestGroup("compileNeg")
    compileFilesInDir("tests/neg", defaultOptions) +
    compileFilesInDir("tests/neg-tailcall", defaultOptions) +
    compileFilesInDir("tests/neg-no-optimise", defaultOptions) +
    compileFilesInDir("tests/neg-kind-polymorphism", defaultOptions and "-Ykind-polymorphism") +
    compileFilesInDir("tests/neg-custom-args/fatal-warnings", defaultOptions.and("-Xfatal-warnings")) +
    compileFilesInDir("tests/neg-custom-args/allow-double-bindings", allowDoubleBindings) +
    compileFile("tests/neg-custom-args/i3246.scala", scala2Mode) +
    compileFile("tests/neg-custom-args/overrideClass.scala", scala2Mode) +
    compileFile("tests/neg-custom-args/autoTuplingTest.scala", defaultOptions.and("-language:noAutoTupling")) +
    compileFile("tests/neg-custom-args/i1050.scala", defaultOptions.and("-strict")) +
    compileFile("tests/neg-custom-args/nopredef.scala", defaultOptions.and("-Yno-predef")) +
    compileFile("tests/neg-custom-args/noimports.scala", defaultOptions.and("-Yno-imports")) +
    compileFile("tests/neg-custom-args/noimports2.scala", defaultOptions.and("-Yno-imports")) +
    compileFile("tests/neg-custom-args/i3882.scala", allowDeepSubtypes) +
    compileFile("tests/neg-custom-args/i1754.scala", allowDeepSubtypes) +
    compileFilesInDir("tests/neg-custom-args/isInstanceOf", allowDeepSubtypes and "-Xfatal-warnings") +
    compileFile("tests/neg-custom-args/i3627.scala", allowDeepSubtypes)
  }.checkExpectedErrors()

  // Run tests -----------------------------------------------------------------

  @Test def runAll: Unit = {
    implicit val testGroup: TestGroup = TestGroup("runAll")
    compileFilesInDir("tests/run", defaultOptions) +
    compileFilesInDir("tests/run-no-optimise", defaultOptions) +
    compileFilesInDir("tests/run-with-compiler", defaultRunWithCompilerOptions)
  }.checkRuns()

  // Generic java signatures tests ---------------------------------------------

  @Test def genericJavaSignatures: Unit = {
    implicit val testGroup: TestGroup = TestGroup("genericJavaSignatures")
    compileFilesInDir("tests/generic-java-signatures", defaultOptions)
  }.checkRuns()

  // Pickling Tests ------------------------------------------------------------
  //
  // Pickling tests are very memory intensive and as such need to be run with a
  // lower level of concurrency as to not kill their running VMs

  @Test def testPickling: Unit = {
    implicit val testGroup: TestGroup = TestGroup("testPickling")
    compileDir("compiler/src/dotty/tools", picklingOptions, recursive = false) +
    compileDir("compiler/src/dotty/tools/dotc", picklingOptions, recursive = false) +
    compileFilesInDir("tests/new", picklingOptions) +
    compileFilesInDir("tests/pickling", picklingOptions) +
    compileDir("library/src/dotty/runtime", picklingOptions) +
    compileDir("compiler/src/dotty/tools/backend/jvm", picklingOptions) +
    compileDir("compiler/src/dotty/tools/dotc/ast", picklingOptions) +
    compileDir("compiler/src/dotty/tools/dotc/core", picklingOptions, recursive = false) +
    compileDir("compiler/src/dotty/tools/dotc/config", picklingOptions) +
    compileDir("compiler/src/dotty/tools/dotc/parsing", picklingOptions) +
    compileDir("compiler/src/dotty/tools/dotc/printing", picklingOptions) +
    compileDir("compiler/src/dotty/tools/repl", picklingOptions) +
    compileDir("compiler/src/dotty/tools/dotc/rewrite", picklingOptions) +
    compileDir("compiler/src/dotty/tools/dotc/transform", picklingOptions) +
    compileDir("compiler/src/dotty/tools/dotc/typer", picklingOptions) +
    compileDir("compiler/src/dotty/tools/dotc/util", picklingOptions) +
    compileDir("compiler/src/dotty/tools/io", picklingOptions) +
    compileFile("tests/pos/pickleinf.scala", picklingOptions) +
    compileDir("compiler/src/dotty/tools/dotc/core/classfile", picklingOptions) +
    compileDir("compiler/src/dotty/tools/dotc/core/tasty", picklingOptions) +
    compileDir("compiler/src/dotty/tools/dotc/core/unpickleScala2", picklingOptions)
  }.limitThreads(4).checkCompile()

  /** The purpose of this test is two-fold, being able to compile dotty
   *  bootstrapped, and making sure that TASTY can link against a compiled
   *  version of Dotty
   */
  @Test def tastyBootstrap: Unit = {
    implicit val testGroup: TestGroup = TestGroup("tastyBootstrap/tests")
    val dotty1Group = TestGroup("tastyBootstrap/dotty1")
    val dotty2Group = TestGroup("tastyBootstrap/dotty2")
    val libGroup = TestGroup("tastyBootstrap/lib")

    val opt = TestFlags(
      // compile with bootstrapped library on cp:
      defaultOutputDir + libGroup + "/src/:" +
      // as well as bootstrapped compiler:
      defaultOutputDir + dotty1Group + "/dotty/:" +
      Jars.dottyInterfaces,
      Array("-Ycheck-reentrant")
    )

    val lib =
      compileDir("library/src",
        defaultOptions.and("-Ycheck-reentrant", "-strict", "-priorityclasspath", defaultOutputDir))(libGroup)

    val compilerDir = Paths.get("compiler/src")
    val compilerSources = sources(Files.walk(compilerDir))

    val backendDir = Paths.get("scala-backend/src/compiler/scala/tools/nsc/backend")
    val backendJvmDir = Paths.get("scala-backend/src/compiler/scala/tools/nsc/backend/jvm")

    // NOTE: Keep these exclusions synchronized with the ones in the sbt build (Build.scala)
    val backendExcluded =
      List("JavaPlatform.scala", "Platform.scala", "ScalaPrimitives.scala")
    val backendJvmExcluded =
      List("BCodeICodeCommon.scala", "GenASM.scala", "GenBCode.scala", "ScalacBackendInterface.scala", "BackendStats.scala", "BCodeAsmEncode.scala")

    val backendSources =
      sources(Files.list(backendDir), excludedFiles = backendExcluded)
    val backendJvmSources =
      sources(Files.list(backendJvmDir), excludedFiles = backendJvmExcluded)

    val dotty1 = compileList("dotty", compilerSources ++ backendSources ++ backendJvmSources, opt)(dotty1Group)
    val dotty2 = compileList("dotty", compilerSources ++ backendSources ++ backendJvmSources, opt)(dotty2Group)

    val tests = {
      lib.keepOutput :: dotty1.keepOutput :: {
        dotty2 +
        compileShallowFilesInDir("compiler/src/dotty/tools", opt) +
        compileShallowFilesInDir("compiler/src/dotty/tools/dotc", opt) +
        compileShallowFilesInDir("compiler/src/dotty/tools/dotc/ast", opt) +
        compileShallowFilesInDir("compiler/src/dotty/tools/dotc/config", opt) +
        compileShallowFilesInDir("compiler/src/dotty/tools/dotc/parsing", opt) +
        compileShallowFilesInDir("compiler/src/dotty/tools/dotc/printing", opt) +
        compileShallowFilesInDir("compiler/src/dotty/tools/dotc/reporting", opt) +
        compileShallowFilesInDir("compiler/src/dotty/tools/dotc/rewrite", opt) +
        compileShallowFilesInDir("compiler/src/dotty/tools/dotc/transform", opt) +
        compileShallowFilesInDir("compiler/src/dotty/tools/dotc/typer", opt) +
        compileShallowFilesInDir("compiler/src/dotty/tools/dotc/util", opt) +
        compileList("shallow-backend", backendSources, opt) +
        compileList("shallow-backend-jvm", backendJvmSources, opt)
      }.keepOutput :: Nil
    }.map(_.checkCompile())

    assert(new java.io.File(s"out/$dotty1Group/dotty/").exists)
    assert(new java.io.File(s"out/$dotty2Group/dotty/").exists)
    assert(new java.io.File(s"out/$libGroup/src/").exists)
    compileList("idempotency", List("tests/idempotency/BootstrapChecker.scala", "tests/idempotency/IdempotencyCheck.scala"), defaultOptions).checkRuns()

    tests.foreach(_.delete())
  }

  @Category(Array(classOf[SlowTests]))
  @Test def testOptimised: Unit = {
    implicit val testGroup: TestGroup = TestGroup("optimised/testOptimised")
    compileFilesInDir("tests/pos", defaultOptimised).checkCompile()
    compileFilesInDir("tests/run", defaultOptimised).checkRuns()
    compileFilesInDir("tests/neg", defaultOptimised).checkExpectedErrors()
  }

  @Test def testPlugins: Unit = {
    val pluginFile = "plugin.properties"

    // 1. hack with absolute path for -Xplugin
    // 2. copy `pluginFile` to destination
    def compileFilesInDir(dir: String): CompilationTest = {
      val outDir = defaultOutputDir + "testPlugins/"
      val sourceDir = new JFile(dir)

      val dirs = sourceDir.listFiles.foldLeft(List.empty[JFile]) { case (dirs, f) =>
        if (f.isDirectory) f :: dirs else dirs
      }

      val targets = dirs.map { dir =>
        val compileDir = createOutputDirsForDir(dir, sourceDir, outDir)
        import java.nio.file.StandardCopyOption.REPLACE_EXISTING
        Files.copy(dir.toPath.resolve(pluginFile), compileDir.toPath.resolve(pluginFile), REPLACE_EXISTING)
        val flags = TestFlags(classPath, noCheckOptions) and ("-Xplugin:" + compileDir.getAbsolutePath)
        SeparateCompilationSource("testPlugins", dir, flags, compileDir)
      }

      new CompilationTest(targets)
    }

    compileFilesInDir("tests/plugins/neg").checkExpectedErrors()
  }

  private val (compilerSources, backendSources, backendJvmSources) = {
    val compilerDir = Paths.get("compiler/src")
    val compilerSources0 = sources(Files.walk(compilerDir))

    val backendDir = Paths.get("scala-backend/src/compiler/scala/tools/nsc/backend")
    val backendJvmDir = Paths.get("scala-backend/src/compiler/scala/tools/nsc/backend/jvm")

    // NOTE: Keep these exclusions synchronized with the ones in the sbt build (Build.scala)
    val backendExcluded =
      List("JavaPlatform.scala", "Platform.scala", "ScalaPrimitives.scala")
    val backendJvmExcluded =
      List("BCodeICodeCommon.scala", "GenASM.scala", "GenBCode.scala", "ScalacBackendInterface.scala", "BackendStats.scala")

    val backendSources0 =
      sources(Files.list(backendDir), excludedFiles = backendExcluded)
    val backendJvmSources0 =
      sources(Files.list(backendJvmDir), excludedFiles = backendJvmExcluded)

    (compilerSources0, backendSources0, backendJvmSources0)
  }
}

object CompilationTests {
  implicit val summaryReport: SummaryReporting = new SummaryReport
  @AfterClass def cleanup(): Unit = summaryReport.echoSummary()

  def sources(paths: JStream[Path], excludedFiles: List[String] = Nil): List[String] = {
    val sources = paths.iterator().asScala
      .filter(path =>
        (path.toString.endsWith(".scala") || path.toString.endsWith(".java"))
          && !excludedFiles.contains(path.getFileName.toString))
      .map(_.toString).toList

    paths.close()
    sources
  }
}
