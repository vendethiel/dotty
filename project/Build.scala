import sbt.Keys._
import sbt._
import complete.DefaultParsers._
import java.io.File
import java.nio.channels.FileLock
import java.nio.file._
import java.util.Calendar

import scala.reflect.io.Path
import sbtassembly.AssemblyKeys.assembly

import xerial.sbt.pack.PackPlugin
import xerial.sbt.pack.PackPlugin.autoImport._

import sbt.Package.ManifestAttributes

import com.typesafe.sbteclipse.plugin.EclipsePlugin._

import dotty.tools.sbtplugin.DottyPlugin.autoImport._
import dotty.tools.sbtplugin.DottyIDEPlugin.{ prepareCommand, runProcess }
import dotty.tools.sbtplugin.DottyIDEPlugin.autoImport._
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

import com.typesafe.sbt.pgp.PgpKeys

import pl.project13.scala.sbt.JmhPlugin
import JmhPlugin.JmhKeys.Jmh

import Modes._

/* In sbt 0.13 the Build trait would expose all vals to the shell, where you
 * can use them in "set a := b" like expressions. This re-exposes them.
 */
object ExposedValues extends AutoPlugin {
  object autoImport {
    val bootstrapFromPublishedJars = Build.bootstrapFromPublishedJars
  }
}

object Build {

  val baseVersion = "0.8.0"
  val scalacVersion = "2.12.4"

  val dottyOrganization = "ch.epfl.lamp"
  val dottyGithubUrl = "https://github.com/lampepfl/dotty"
  val dottyVersion = {
    val isNightly = sys.env.get("NIGHTLYBUILD") == Some("yes")
    val isRelease = sys.env.get("RELEASEBUILD") == Some("yes")
    if (isNightly)
      baseVersion + "-bin-" + VersionUtil.commitDate + "-" + VersionUtil.gitHash + "-NIGHTLY"
    else if (isRelease)
      baseVersion
    else
      baseVersion + "-bin-SNAPSHOT"
  }
  val dottyNonBootstrappedVersion = dottyVersion + "-nonbootstrapped"

  val jenkinsMemLimit = List("-Xmx1500m")

  val JENKINS_BUILD = "dotty.jenkins.build"
  val DRONE_MEM = "dotty.drone.mem"


  val agentOptions = List(
    // "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
    // "-agentpath:/home/dark/opt/yjp-2013-build-13072/bin/linux-x86-64/libyjpagent.so"
    // "-agentpath:/Applications/YourKit_Java_Profiler_2015_build_15052.app/Contents/Resources/bin/mac/libyjpagent.jnilib",
    // "-XX:+HeapDumpOnOutOfMemoryError", "-Xmx1g", "-Xss2m"
  )

  // Packages all subprojects to their jars
  lazy val packageAll =
    taskKey[Map[String, String]]("Package everything needed to run tests")

  // Run tests with filter through vulpix test suite
  lazy val testCompilation = inputKey[Unit]("runs integration test with the supplied filter")

  // Spawns a repl with the correct classpath
  lazy val repl = inputKey[Unit]("run the REPL with correct classpath")

  // Used to compile files similar to ./bin/dotc script
  lazy val dotc =
    inputKey[Unit]("run the compiler using the correct classpath, or the user supplied classpath")

  // Used to run binaries similar to ./bin/dotr script
  lazy val dotr =
    inputKey[Unit]("run compiled binary using the correct classpath, or the user supplied classpath")


  // Compiles the documentation and static site
  lazy val genDocs = taskKey[Unit]("run dottydoc to generate static documentation site")

  // Shorthand for compiling a docs site
  lazy val dottydoc = inputKey[Unit]("run dottydoc")

  lazy val bootstrapFromPublishedJars = settingKey[Boolean]("If true, bootstrap dotty from published non-bootstrapped dotty")

  // Only available in vscode-dotty
  lazy val unpublish = taskKey[Unit]("Unpublish a package")

  // Settings shared by the build (scoped in ThisBuild). Used in build.sbt
  lazy val thisBuildSettings = Def.settings(
    organization := dottyOrganization,
    organizationName := "LAMP/EPFL",
    organizationHomepage := Some(url("http://lamp.epfl.ch")),

    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-Xfatal-warnings",
      "-encoding", "UTF8",
      "-language:existentials,higherKinds,implicitConversions"
    ),

    javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),

    // Change this to true if you want to bootstrap using a published non-bootstrapped compiler
    bootstrapFromPublishedJars := false,

    // Override `runCode` from sbt-dotty to use the language-server and
    // vscode extension from the source repository of dotty instead of a
    // published version.
    runCode := (run in `dotty-language-server`).toTask("").value,

    // include sources in eclipse (downloads source code for all dependencies)
    //http://stackoverflow.com/questions/10472840/how-to-attach-sources-to-sbt-managed-dependencies-in-scala-ide#answer-11683728
    EclipseKeys.withSource := true
  )

  // Settings shared globally (scoped in Global). Used in build.sbt
  lazy val globalSettings = Def.settings(
    // Override `runCode` from sbt-dotty to use the language-server and
    // vscode extension from the source repository of dotty instead of a
    // published version.
    runCode := (run in `dotty-language-server`).toTask("").value,

    onLoad := (onLoad in Global).value andThen { state =>
      def exists(submodule: String) = {
        val path = Paths.get(submodule)
        Files.exists(path) && {
          val fileStream = Files.list(path)
          val nonEmpty = fileStream.iterator().hasNext()
          fileStream.close()
          nonEmpty
        }
      }

      // Make sure all submodules are properly cloned
      val submodules = List("scala-backend", "scala2-library", "collection-strawman")
      if (!submodules.forall(exists)) {
        sLog.value.log(Level.Error,
          s"""Missing some of the submodules
             |You can initialize the modules with:
             |  > git submodule update --init
          """.stripMargin)
      }

      // Copy default configuration from .vscode-template/ unless configuration files already exist in .vscode/
      sbt.IO.copyDirectory(new File(".vscode-template/"), new File(".vscode/"), overwrite = false)

      state
    },

    // Credentials to release to Sonatype
    credentials ++= (
      for {
        username <- sys.env.get("SONATYPE_USER")
        password <- sys.env.get("SONATYPE_PW")
      } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)
    ).toList,
    PgpKeys.pgpPassphrase := sys.env.get("PGP_PW").map(_.toCharArray())
  )

  lazy val commonSettings = publishSettings ++ Seq(
    scalaSource       in Compile    := baseDirectory.value / "src",
    scalaSource       in Test       := baseDirectory.value / "test",
    javaSource        in Compile    := baseDirectory.value / "src",
    javaSource        in Test       := baseDirectory.value / "test",
    resourceDirectory in Compile    := baseDirectory.value / "resources",
    resourceDirectory in Test       := baseDirectory.value / "test-resources",

    // Prevent sbt from rewriting our dependencies
    ivyScala ~= (_ map (_ copy (overrideScalaVersion = false))),

    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % Test,

    // enable verbose exception messages for JUnit
    testOptions in Test += Tests.Argument(TestFrameworks.JUnit, "-a", "-v")
  )

  // Settings used for projects compiled only with Scala 2
  lazy val commonScala2Settings = commonSettings ++ Seq(
    version := dottyVersion,
    scalaVersion := scalacVersion
  )

  // Settings used when compiling dotty using Scala 2
  lazy val commonNonBootstrappedSettings = commonSettings ++ publishSettings ++ Seq(
    version := dottyNonBootstrappedVersion,
    scalaVersion := scalacVersion
  )

  // Settings used when compiling dotty with a non-bootstrapped dotty
  lazy val commonBootstrappedSettings = commonSettings ++ Seq(
    EclipseKeys.skipProject := true,
    version := dottyVersion,
    scalaVersion := dottyNonBootstrappedVersion,

    // Avoid having to run `dotty-sbt-bridge/publishLocal` before compiling a bootstrapped project
    scalaCompilerBridgeSource :=
      (dottyOrganization %% "dotty-sbt-bridge" % dottyVersion % Configurations.Component.name)
      .artifacts(Artifact.sources("dotty-sbt-bridge").copy(url =
        // We cannot use the `packageSrc` task because a setting cannot depend
        // on a task. Instead, we make `compile` below depend on the bridge `packageSrc`
        Some((artifactPath in (`dotty-sbt-bridge`, Compile, packageSrc)).value.toURI.toURL))),
    compile in Compile := (compile in Compile)
      .dependsOn(packageSrc in (`dotty-sbt-bridge`, Compile)).value,

    // Use the same name as the non-bootstrapped projects for the artifacts
    moduleName ~= { _.stripSuffix("-bootstrapped") },

    // Prevent sbt from setting the Scala bootclasspath, otherwise it will
    // contain `scalaInstance.value.libraryJar` which in our case is the
    // non-bootstrapped dotty-library that will then take priority over
    // the bootstrapped dotty-library on the classpath or sourcepath.
    classpathOptions ~= (_.copy(autoBoot = false)),
    // We still need a Scala bootclasspath equal to the JVM bootclasspath,
    // otherwise sbt 0.13 incremental compilation breaks (https://github.com/sbt/sbt/issues/3142)
    scalacOptions ++= Seq("-bootclasspath", sys.props("sun.boot.class.path")),

    // sbt gets very unhappy if two projects use the same target
    target := baseDirectory.value / ".." / "out" / "bootstrap" / name.value,

    // The non-bootstrapped dotty-library is not necessary when bootstrapping dotty
    autoScalaLibrary := false,
    // ...but scala-library is
    libraryDependencies += "org.scala-lang" % "scala-library" % scalacVersion,

    ivyConfigurations ++= {
      if (bootstrapFromPublishedJars.value)
        Seq(Configurations.ScalaTool)
      else
        Seq()
    },
    libraryDependencies ++= {
      if (bootstrapFromPublishedJars.value)
        Seq(
          dottyOrganization %% "dotty-library" % dottyNonBootstrappedVersion % Configurations.ScalaTool.name,
          dottyOrganization %% "dotty-compiler" % dottyNonBootstrappedVersion % Configurations.ScalaTool.name
        ).map(_.withDottyCompat())
      else
        Seq()
    },

    // Compile using the non-bootstrapped and non-published dotty
    managedScalaInstance := false,
    scalaInstance := {
      val (libraryJar, compilerJar) =
        if (bootstrapFromPublishedJars.value) {
          val jars = update.value.select(
            configuration = configurationFilter(Configurations.ScalaTool.name),
            artifact = artifactFilter(extension = "jar")
          )
          (jars.find(_.getName.startsWith("dotty-library_2.12")).get,
           jars.find(_.getName.startsWith("dotty-compiler_2.12")).get)
        } else
          ((packageBin in (`dotty-library`, Compile)).value,
           (packageBin in (`dotty-compiler`, Compile)).value)

      // All compiler dependencies except the library
      val otherDependencies = (dependencyClasspath in (`dotty-compiler`, Compile)).value
        .filterNot(_.get(artifact.key).exists(_.name == "dotty-library"))
        .map(_.data)

      val loader = state.value.classLoaderCache(libraryJar :: compilerJar :: otherDependencies.toList)
      new ScalaInstance(scalaVersion.value, loader, libraryJar, compilerJar, otherDependencies, None)
    }
  )


  // Bootstrap with -optimise
  lazy val commonOptimisedSettings = commonBootstrappedSettings ++ Seq(
    scalacOptions ++= Seq("-optimise"),

    // The *-bootstrapped and *-optimised projects contain the same sources, so
    // we only need to import one set in the IDE. We prefer to import the
    // non-optimized projects because optimize is slower to compile and we do
    // not trust its output yet.
    excludeFromIDE := true
  )

  lazy val commonBenchmarkSettings = Seq(
    outputStrategy := Some(StdoutOutput),
    mainClass in (Jmh, run) := Some("dotty.tools.benchmarks.Bench"), // custom main for jmh:run
    javaOptions += "-DBENCH_CLASS_PATH=" + Attributed.data((fullClasspath in Compile).value).mkString("", ":", "")
  )

  // sbt >= 0.13.12 will automatically rewrite transitive dependencies on
  // any version in any organization of scala{-library,-compiler,-reflect,p}
  // to have organization `scalaOrganization` and version `scalaVersion`
  // (see https://github.com/sbt/sbt/pull/2634).
  // This means that we need to provide dummy artefacts for these projects,
  // otherwise users will get compilation errors if they happen to transitively
  // depend on one of these projects.
  lazy val commonDummySettings = commonBootstrappedSettings ++ Seq(
    crossPaths := false,
    libraryDependencies := Seq()
  )

  /** Projects -------------------------------------------------------------- */

  // Needed because the dotty project aggregates dotty-sbt-bridge but dotty-sbt-bridge
  // currently refers to dotty in its scripted task and "aggregate" does not take by-name
  // parameters: https://github.com/sbt/sbt/issues/2200
  lazy val dottySbtBridgeRef = LocalProject("dotty-sbt-bridge")
  // Same thing for the bootstrapped version
  lazy val dottySbtBridgeBootstrappedRef = LocalProject("dotty-sbt-bridge-bootstrapped")

  def dottySbtBridgeReference(implicit mode: Mode): LocalProject = mode match {
    case NonBootstrapped => dottySbtBridgeRef
    case _ => dottySbtBridgeBootstrappedRef
  }

  // The root project:
  // - aggregates other projects so that "compile", "test", etc are run on all projects at once.
  // - publishes its own empty artifact "dotty" that depends on "dotty-library" and "dotty-compiler",
  //   this is only necessary for compatibility with sbt which currently hardcodes the "dotty" artifact name
  lazy val dotty = project.in(file(".")).asDottyRoot(NonBootstrapped)
  lazy val `dotty-bootstrapped` = project.asDottyRoot(Bootstrapped)
  lazy val `dotty-optimised` = project.asDottyRoot(BootstrappedOptimised)

  lazy val `dotty-interfaces` = project.in(file("interfaces")).
    settings(commonScala2Settings). // Java-only project, so this is fine
    settings(
      // Do not append Scala versions to the generated artifacts
      crossPaths := false,
      // Do not depend on the Scala library
      autoScalaLibrary := false,
      // Let the sbt eclipse plugin know that this is a Java-only project
      EclipseKeys.projectFlavor := EclipseProjectFlavor.Java,
      //Remove javac invalid options in Compile doc
      javacOptions in (Compile, doc) --= Seq("-Xlint:unchecked", "-Xlint:deprecation")
    )

  private lazy val dottydocClasspath = Def.task {
    val dottyLib = (packageAll in `dotty-compiler`).value("dotty-library")
    val dottyInterfaces = (packageAll in `dotty-compiler`).value("dotty-interfaces")
    val otherDeps = (dependencyClasspath in Compile).value.map(_.data).mkString(File.pathSeparator)
    dottyLib + File.pathSeparator + dottyInterfaces + File.pathSeparator + otherDeps
  }
    
  // Settings shared between dotty-doc and dotty-doc-bootstrapped
  lazy val dottyDocSettings = Seq(
    baseDirectory in (Compile, run) := baseDirectory.value / "..",
    baseDirectory in Test := baseDirectory.value / "..",

    connectInput in run := true,
    outputStrategy := Some(StdoutOutput),

    javaOptions ++= (javaOptions in `dotty-compiler`).value,
    fork in run := true,
    fork in Test := true,
    parallelExecution in Test := false,

    genDocs := Def.taskDyn {
      // Make majorVersion available at dotty.epfl.ch/versions/latest-nightly-base
      // Used by sbt-dotty to resolve the latest nightly
      val majorVersion = baseVersion.take(baseVersion.lastIndexOf('.'))
      IO.write(file("./docs/_site/versions/latest-nightly-base"), majorVersion)
      
      val sources =
        (unmanagedSources in (Compile, compile)).value ++
          (unmanagedSources in (`dotty-compiler`, Compile)).value
      val args: Seq[String] = Seq(
        "-siteroot", "docs",
        "-project", "Dotty",
        "-project-version", dottyVersion,
        "-project-url", dottyGithubUrl,
        "-classpath", dottydocClasspath.value
      )
      (runMain in Compile).toTask(
        s""" dotty.tools.dottydoc.Main ${args.mkString(" ")} ${sources.mkString(" ")}"""
      )
    }.value,

    dottydoc := Def.inputTaskDyn {
      val args: Seq[String] = spaceDelimited("<arg>").parsed
      val cp: Seq[String] = Seq("-classpath", dottydocClasspath.value)
        (runMain in Compile).toTask(s""" dotty.tools.dottydoc.Main ${cp.mkString(" ")} """ + args.mkString(" "))
    }.evaluated,

    libraryDependencies ++= {
      val flexmarkVersion = "0.28.32"
      Seq(
        "com.vladsch.flexmark" % "flexmark" % flexmarkVersion,
        "com.vladsch.flexmark" % "flexmark-ext-gfm-tasklist" % flexmarkVersion,
        "com.vladsch.flexmark" % "flexmark-ext-gfm-tables" % flexmarkVersion,
        "com.vladsch.flexmark" % "flexmark-ext-autolink" % flexmarkVersion,
        "com.vladsch.flexmark" % "flexmark-ext-anchorlink" % flexmarkVersion,
        "com.vladsch.flexmark" % "flexmark-ext-emoji" % flexmarkVersion,
        "com.vladsch.flexmark" % "flexmark-ext-gfm-strikethrough" % flexmarkVersion,
        "com.vladsch.flexmark" % "flexmark-ext-yaml-front-matter" % flexmarkVersion,
        Dependencies.`jackson-dataformat-yaml`,
        "nl.big-o" % "liqp" % "0.6.7"
      )
    }
  )

  lazy val `dotty-doc` = project.in(file("doc-tool")).asDottyDoc(NonBootstrapped)
  lazy val `dotty-doc-bootstrapped` = project.in(file("doc-tool")).asDottyDoc(Bootstrapped)
  lazy val `dotty-doc-optimised` = project.in(file("doc-tool")).asDottyDoc(BootstrappedOptimised)

  def dottyDoc(implicit mode: Mode): Project = mode match {
    case NonBootstrapped => `dotty-doc`
    case Bootstrapped => `dotty-doc-bootstrapped`
    case BootstrappedOptimised => `dotty-doc-optimised`
  }

  // Settings shared between dotty-compiler and dotty-compiler-bootstrapped
  lazy val commonDottyCompilerSettings = Seq(

      // The scala-backend folder is a git submodule that contains a fork of the Scala 2.11
      // compiler developed at https://github.com/lampepfl/scala/tree/sharing-backend.
      // We do not compile the whole submodule, only the part of the Scala 2.11 GenBCode backend
      // that we reuse for dotty.
      // See http://dotty.epfl.ch/docs/contributing/backend.html for more information.
      //
      // NOTE: We link (or copy if symbolic links are not supported) these sources in
      // the current project using `sourceGenerators` instead of simply
      // referencing them using `unmanagedSourceDirectories` because the latter
      // breaks some IDEs.
      sourceGenerators in Compile += Def.task {
        val outputDir = (sourceManaged in Compile).value

        val submoduleCompilerDir = baseDirectory.value / ".." / "scala-backend" / "src" / "compiler"
        val backendDir = submoduleCompilerDir / "scala" / "tools" / "nsc" / "backend"
        val allScalaFiles = GlobFilter("*.scala")

        // NOTE: Keep these exclusions synchronized with the ones in the tests (CompilationTests.scala)
        val files = ((backendDir *
          (allScalaFiles - "JavaPlatform.scala" - "Platform.scala" - "ScalaPrimitives.scala")) +++
         (backendDir / "jvm") *
          (allScalaFiles - "BCodeICodeCommon.scala" - "GenASM.scala" - "GenBCode.scala" - "ScalacBackendInterface.scala" - "BackendStats.scala")
        ).get

        val pairs = files.pair(sbt.Path.rebase(submoduleCompilerDir, outputDir))

        try {
          pairs.foreach { case (src, dst) =>
            sbt.IO.createDirectory(dst.getParentFile)
            if (!dst.exists)
              Files.createSymbolicLink(/*link = */ dst.toPath, /*existing = */src.toPath)
          }
        } catch {
          case _: UnsupportedOperationException | _: FileSystemException =>
            // If the OS doesn't support symbolic links, copy the directory instead.
            sbt.IO.copy(pairs, overwrite = true, preserveLastModified = true)
        }

        pairs.map(_._2)
      }.taskValue,

      // set system in/out for repl
      connectInput in run := true,
      outputStrategy := Some(StdoutOutput),

      // Generate compiler.properties, used by sbt
      resourceGenerators in Compile += Def.task {
        import java.util._
        import java.text._
        val file = (resourceManaged in Compile).value / "compiler.properties"
        val dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss")
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"))
        val contents =                //2.11.11.v20170413-090219-8a413ba7cc
          s"""version.number=${version.value}
             |maven.version.number=${version.value}
             |git.hash=${VersionUtil.gitHash}
             |copyright.string=Copyright 2002-${Calendar.getInstance().get(Calendar.YEAR)}, LAMP/EPFL
           """.stripMargin

        if (!(file.exists && IO.read(file) == contents)) {
          IO.write(file, contents)
        }

        Seq(file)
      }.taskValue,

      // get libraries onboard
      libraryDependencies ++= Seq(
        "org.scala-lang.modules" % "scala-asm" % "6.0.0-scala-1", // used by the backend
        "com.typesafe.sbt" % "sbt-interface" % sbtVersion.value,
        ("org.scala-lang.modules" %% "scala-xml" % "1.0.6").withDottyCompat(),
        "org.scala-lang" % "scala-library" % scalacVersion % "test"
      ),

      // For convenience, change the baseDirectory when running the compiler
      baseDirectory in (Compile, run) := baseDirectory.value / "..",
      // And when running the tests
      baseDirectory in Test := baseDirectory.value / "..",

      test in Test := {
        // Exclude VulpixMetaTests
        (testOnly in Test).toTask(" -- --exclude-categories=dotty.VulpixMetaTests").value
      },

      testOptions in Test += Tests.Argument(
        TestFrameworks.JUnit, "--run-listener=dotty.tools.ContextEscapeDetector"
      ),

      // Spawn new JVM in run and test
      fork in run := true,
      fork in Test := true,
      parallelExecution in Test := false,

      // Add git-hash used to package the distribution to the manifest to know it in runtime and report it in REPL
      packageOptions += ManifestAttributes(("Git-Hash", VersionUtil.gitHash)),

      // http://grokbase.com/t/gg/simple-build-tool/135ke5y90p/sbt-setting-jvm-boot-paramaters-for-scala
      // packageAll should always be run before tests
      javaOptions ++= {
        val attList = (dependencyClasspath in Runtime).value
        val pA = packageAll.value

        // put needed dependencies on classpath:
        val path = for {
          file <- attList.map(_.data)
          path = file.getAbsolutePath
          // FIXME: when we snip the cord, this should go bye-bye
          if path.contains("scala-library") ||
            // FIXME: currently needed for tests referencing scalac internals
            path.contains("scala-reflect") ||
            // FIXME: should go away when xml literal parsing is removed
            path.contains("scala-xml") ||
            // used for tests that compile dotty
            path.contains("scala-asm") ||
            // needed for the xsbti interface
            path.contains("sbt-interface")
        } yield "-Xbootclasspath/p:" + path

        val ci_build = // propagate if this is a ci build
          if (sys.props.isDefinedAt(JENKINS_BUILD))
            List(s"-D$JENKINS_BUILD=${sys.props(JENKINS_BUILD)}") ::: jenkinsMemLimit
          else if (sys.props.isDefinedAt(DRONE_MEM))
            List("-Xmx" + sys.props(DRONE_MEM))
          else List()

        val tuning =
          if (sys.props.isDefinedAt("Oshort"))
            // Optimize for short-running applications, see https://github.com/lampepfl/dotty/issues/222
            List("-XX:+TieredCompilation", "-XX:TieredStopAtLevel=1")
          else List()

        val jars = List(
          "-Ddotty.tests.classes.interfaces=" + pA("dotty-interfaces"),
          "-Ddotty.tests.classes.library=" + pA("dotty-library"),
          "-Ddotty.tests.classes.compiler=" + pA("dotty-compiler")
        )

        jars ::: tuning ::: agentOptions ::: ci_build ::: path.toList
      },

      testCompilation := Def.inputTaskDyn {
        val args: Seq[String] = spaceDelimited("<arg>").parsed
        val cmd = " dotty.tools.dotc.CompilationTests -- --exclude-categories=dotty.SlowTests" + {
          if (args.nonEmpty) " -Ddotty.tests.filter=" + args.mkString(" ")
          else ""
        }
        (testOnly in Test).toTask(cmd)
      }.evaluated,

      // Override run to be able to run compiled classfiles
      dotr := {
        val args: List[String] = spaceDelimited("<arg>").parsed.toList
        val java: String = Process("which" :: "java" :: Nil).!!
        val attList = (dependencyClasspath in Runtime).value
        val _ = packageAll.value

        def findLib(name: String) = attList
          .map(_.data.getAbsolutePath)
          .find(_.contains(name))
          .toList.mkString(":")

        val scalaLib = findLib("scala-library")
        val dottyLib = packageAll.value("dotty-library")

        def run(args: List[String]): Unit = {
          val fullArgs = insertClasspathInArgs(args, s".:$dottyLib:$scalaLib")
          s"$java ${fullArgs.mkString(" ")}".!
        }

        if (args.isEmpty) {
          println("Couldn't run `dotr` without args. Use `repl` to run the repl or add args to run the dotty application")
        } else if (java == "") {
          println("Couldn't find java executable on path, please install java to a default location")
        } else if (scalaLib == "") {
          println("Couldn't find scala-library on classpath, please run using script in bin dir instead")
        } else if (args.contains("-with-compiler")) {
          val args1 = args.filter(_ != "-with-compiler")
          val asm = findLib("scala-asm")
          val dottyCompiler = packageAll.value("dotty-compiler")
          val dottyInterfaces = packageAll.value("dotty-interfaces")
          run(insertClasspathInArgs(args1, s"$dottyCompiler:$dottyInterfaces:$asm"))
        } else run(args)
      },
      run := dotc.evaluated,
      dotc := runCompilerMain().evaluated,
      repl := runCompilerMain(repl = true).evaluated

      /* Add the sources of scalajs-ir.
       * To guarantee that dotty can bootstrap without depending on a version
       * of scalajs-ir built with a different Scala compiler, we add its
       * sources instead of depending on the binaries.
       */
      //TODO: disabling until moved to separate project
      //ivyConfigurations += config("sourcedeps").hide,
      //libraryDependencies +=
      //  "org.scala-js" %% "scalajs-ir" % scalaJSVersion % "sourcedeps",
      //sourceGenerators in Compile += Def.task {
      //  val s = streams.value
      //  val cacheDir = s.cacheDirectory
      //  val trgDir = (sourceManaged in Compile).value / "scalajs-ir-src"

      //  val report = updateClassifiers.value
      //  val scalaJSIRSourcesJar = report.select(
      //      configuration = Set("sourcedeps"),
      //      module = (_: ModuleID).name.startsWith("scalajs-ir_"),
      //      artifact = artifactFilter(`type` = "src")).headOption.getOrElse {
      //    sys.error(s"Could not fetch scalajs-ir sources")
      //  }

      //  FileFunction.cached(cacheDir / s"fetchScalaJSIRSource",
      //      FilesInfo.lastModified, FilesInfo.exists) { dependencies =>
      //    s.log.info(s"Unpacking scalajs-ir sources to $trgDir...")
      //    if (trgDir.exists)
      //      IO.delete(trgDir)
      //    IO.createDirectory(trgDir)
      //    IO.unzip(scalaJSIRSourcesJar, trgDir)
      //    (trgDir ** "*.scala").get.toSet
      //  } (Set(scalaJSIRSourcesJar)).toSeq
      //}.taskValue
  )

  def runCompilerMain(repl: Boolean = false) = Def.inputTaskDyn {
    val dottyLib = packageAll.value("dotty-library")
    lazy val dottyCompiler = packageAll.value("dotty-compiler")
    val args0: List[String] = spaceDelimited("<arg>").parsed.toList
    val decompile = args0.contains("-decompile")
    val debugFromTasty = args0.contains("-Ythrough-tasty")
    val args = args0.filter(arg => arg != "-repl" && arg != "-decompile" &&
        arg != "-with-compiler" && arg != "-Ythrough-tasty")

    val main =
      if (repl) "dotty.tools.repl.Main"
      else if (decompile) "dotty.tools.dotc.decompiler.Main"
      else if (debugFromTasty) "dotty.tools.dotc.fromtasty.Debug"
      else "dotty.tools.dotc.Main"

    var extraClasspath = dottyLib
    if (decompile && !args.contains("-classpath")) extraClasspath += ":."
    if (args0.contains("-with-compiler")) extraClasspath += s":$dottyCompiler"

    val fullArgs = main :: insertClasspathInArgs(args, extraClasspath)

    (runMain in Compile).toTask(fullArgs.mkString(" ", " ", ""))
  }

  def insertClasspathInArgs(args: List[String], cp: String): List[String] = {
    val (beforeCp, fromCp) = args.span(_ != "-classpath")
    val classpath = fromCp.drop(1).headOption.fold(cp)(_ + ":" + cp)
    "-classpath" :: classpath :: beforeCp ::: fromCp.drop(2)
  }

  lazy val nonBootstrapedDottyCompilerSettings = commonDottyCompilerSettings ++ Seq(
    // Disable scaladoc generation, it's way too slow and we'll replace it
    // by dottydoc anyway. We still publish an empty -javadoc.jar to make
    // sonatype happy.
    sources in (Compile, doc) := Seq(),

    // packageAll packages all and then returns a map with the abs location
    packageAll := {
      Map(
        "dotty-interfaces" -> (packageBin in (`dotty-interfaces`, Compile)).value,
        "dotty-compiler" -> (packageBin in Compile).value,
        "dotty-library" -> (packageBin in (`dotty-library`, Compile)).value,
        "dotty-compiler-test" -> (packageBin in Test).value
      ).mapValues(_.getAbsolutePath)
    }
  )

  lazy val bootstrapedDottyCompilerSettings = commonDottyCompilerSettings ++ Seq(
    packageAll := {
      (packageAll in `dotty-compiler`).value ++ Seq(
        ("dotty-compiler" -> (packageBin in Compile).value.getAbsolutePath),
        ("dotty-library" -> (packageBin in (dottyLibrary(Bootstrapped), Compile)).value.getAbsolutePath)
      )
    }
  )

  def dottyCompilerSettings(implicit mode: Mode): sbt.Def.SettingsDefinition =
    if (mode == NonBootstrapped) nonBootstrapedDottyCompilerSettings else bootstrapedDottyCompilerSettings

  lazy val `dotty-compiler` = project.in(file("compiler")).asDottyCompiler(NonBootstrapped)
  lazy val `dotty-compiler-bootstrapped` = project.in(file("compiler")).asDottyCompiler(Bootstrapped)
  lazy val `dotty-compiler-optimised` = project.in(file("compiler")).asDottyCompiler(BootstrappedOptimised)

  def dottyCompiler(implicit mode: Mode): Project = mode match {
    case NonBootstrapped => `dotty-compiler`
    case Bootstrapped => `dotty-compiler-bootstrapped`
    case BootstrappedOptimised => `dotty-compiler-optimised`
  }

  // Settings shared between dotty-library and dotty-library-bootstrapped
  lazy val dottyLibrarySettings = Seq(
      libraryDependencies += "org.scala-lang" % "scala-library" % scalacVersion
  )

  lazy val `dotty-library` = project.in(file("library")).asDottyLibrary(NonBootstrapped)
  lazy val `dotty-library-bootstrapped`: Project = project.in(file("library")).asDottyLibrary(Bootstrapped)
  lazy val `dotty-library-optimised`: Project = project.in(file("library")).asDottyLibrary(BootstrappedOptimised)

  def dottyLibrary(implicit mode: Mode): Project = mode match {
    case NonBootstrapped => `dotty-library`
    case Bootstrapped => `dotty-library-bootstrapped`
    case BootstrappedOptimised => `dotty-library-optimised`
  }

  // until sbt/sbt#2402 is fixed (https://github.com/sbt/sbt/issues/2402)
  lazy val cleanSbtBridge = TaskKey[Unit]("cleanSbtBridge", "delete dotty-sbt-bridge cache")

  lazy val dottySbtBridgeSettings = Seq(
    cleanSbtBridge := {
      val home = System.getProperty("user.home")
      val sbtOrg = "org.scala-sbt"
      val bridgeDirectoryPattern = s"*${dottyVersion}*"

      val log = streams.value.log
      log.info("Cleaning the dotty-sbt-bridge cache")
      IO.delete((file(home) / ".ivy2" / "cache" / sbtOrg * bridgeDirectoryPattern).get)
      IO.delete((file(home) / ".sbt"  / "boot" * "scala-*" / sbtOrg / "sbt" * "*" * bridgeDirectoryPattern).get)
    },
    compile in Compile := (compile in Compile).dependsOn(cleanSbtBridge).value,
    description := "sbt compiler bridge for Dotty",
    resolvers += Resolver.typesafeIvyRepo("releases"), // For org.scala-sbt:api
    libraryDependencies ++= Seq(
      "com.typesafe.sbt" % "sbt-interface" % sbtVersion.value,
      "org.scala-sbt" % "api" % sbtVersion.value % "test",
      ("org.specs2" %% "specs2-core" % "3.9.1" % "test").withDottyCompat(),
      ("org.specs2" %% "specs2-junit" % "3.9.1" % "test").withDottyCompat()
    ),
    // The sources should be published with crossPaths := false since they
    // need to be compiled by the project using the bridge.
    crossPaths := false,

    // Don't publish any binaries for the bridge because of the above
    publishArtifact in (Compile, packageBin) := false,

    fork in Test := true,
    parallelExecution in Test := false
  )

  lazy val `dotty-sbt-bridge` = project.in(file("sbt-bridge")).asDottySbtBridge(NonBootstrapped)
  lazy val `dotty-sbt-bridge-bootstrapped` = project.in(file("sbt-bridge")).asDottySbtBridge(Bootstrapped)

  lazy val `dotty-language-server` = project.in(file("language-server")).
    dependsOn(dottyCompiler(Bootstrapped)).
    settings(commonBootstrappedSettings).
    settings(
      // Sources representing the shared configuration file used to communicate between the sbt-dotty
      // plugin and the language server
      unmanagedSourceDirectories in Compile += baseDirectory.value / "../sbt-dotty/src/dotty/tools/sbtplugin/config",

      // fork so that the shutdown hook in Main is run when we ctrl+c a run
      // (you need to have `cancelable in Global := true` in your global sbt config to ctrl+c a run)
      fork in run := true,
      fork in Test := true,
      libraryDependencies ++= Seq(
        "org.eclipse.lsp4j" % "org.eclipse.lsp4j" % "0.3.0",
        Dependencies.`jackson-databind`
      ),
      javaOptions := (javaOptions in `dotty-compiler-bootstrapped`).value,

      run := Def.inputTaskDyn {
        val inputArgs = spaceDelimited("<arg>").parsed

        val mainClass = "dotty.tools.languageserver.Main"
        val extensionPath = (baseDirectory in `vscode-dotty`).value.getAbsolutePath

        val codeArgs =
          s"--extensionDevelopmentPath=$extensionPath" +:
            (if (inputArgs.isEmpty) List((baseDirectory.value / "..").getAbsolutePath) else inputArgs)

        val clientCommand = prepareCommand(codeCommand.value ++ codeArgs)

        val allArgs = "-client_command" +: clientCommand

        runTask(Runtime, mainClass, allArgs: _*)
      }.dependsOn(compile in (`vscode-dotty`, Compile)).evaluated
    )

  /** A sandbox to play with the Scala.js back-end of dotty.
   *
   *  This sandbox is compiled with dotty with support for Scala.js. It can be
   *  used like any regular Scala.js project. In particular, `fastOptJS` will
   *  produce a .js file, and `run` will run the JavaScript code with a JS VM.
   *
   *  Simply running `dotty/run -scalajs` without this sandbox is not very
   *  useful, as that would not provide the linker and JS runners.
   */
  lazy val sjsSandbox = project.in(file("sandbox/scalajs")).
    enablePlugins(ScalaJSPlugin).
    settings(commonNonBootstrappedSettings).
    settings(
      /* Remove the Scala.js compiler plugin for scalac, and enable the
       * Scala.js back-end of dotty instead.
       */
      libraryDependencies ~= { deps =>
        deps.filterNot(_.name.startsWith("scalajs-compiler"))
      },
      scalacOptions += "-scalajs",

      // The main class cannot be found automatically due to the empty inc.Analysis
      mainClass in Compile := Some("hello.world"),

      // While developing the Scala.js back-end, it is very useful to see the trees dotc gives us
      scalacOptions += "-Xprint:labelDef",

      /* Debug-friendly Scala.js optimizer options.
       * In particular, typecheck the Scala.js IR found on the classpath.
       */
      scalaJSOptimizerOptions ~= {
        _.withCheckScalaJSIR(true).withParallel(false)
      }
    ).
    settings(compileWithDottySettings).
    settings(inConfig(Compile)(Seq(
      /* Make sure jsDependencyManifest runs after compile, otherwise compile
       * might remove the entire directory afterwards.
       */
      jsDependencyManifest := jsDependencyManifest.dependsOn(compile).value
    )))

  lazy val `dotty-bench` = project.in(file("bench")).asDottyBench(NonBootstrapped)
  lazy val `dotty-bench-bootstrapped` = project.in(file("bench")).asDottyBench(Bootstrapped)
  lazy val `dotty-bench-optimised` = project.in(file("bench")).asDottyBench(BootstrappedOptimised)

  // Depend on dotty-library so that sbt projects using dotty automatically
  // depend on the dotty-library
  lazy val `scala-library` = project.
    dependsOn(`dotty-library-bootstrapped`).
    settings(commonDummySettings).
    settings(
      // Need a direct dependency on the real scala-library even though we indirectly
      // depend on it via dotty-library, because sbt may rewrite dependencies
      // (see https://github.com/sbt/sbt/pull/2634), but won't rewrite the direct
      // dependencies of scala-library (see https://github.com/sbt/sbt/pull/2897)
      libraryDependencies += "org.scala-lang" % "scala-library" % scalacVersion
    )

  lazy val `scala-compiler` = project.
    settings(commonDummySettings)
  lazy val `scala-reflect` = project.
    settings(commonDummySettings).
    settings(
      libraryDependencies := Seq("org.scala-lang" % "scala-reflect" % scalacVersion)
    )
  lazy val scalap = project.
    settings(commonDummySettings).
    settings(
      libraryDependencies := Seq("org.scala-lang" % "scalap" % scalacVersion)
    )


  // sbt plugin to use Dotty in your own build, see
  // https://github.com/lampepfl/dotty-example-project for usage.
  lazy val `sbt-dotty` = project.in(file("sbt-dotty")).
    settings(commonSettings).
    settings(
      // Keep in sync with inject-sbt-dotty.sbt
      libraryDependencies += Dependencies.`jackson-databind`,
      unmanagedSourceDirectories in Compile +=
        baseDirectory.value / "../language-server/src/dotty/tools/languageserver/config",


      sbtPlugin := true,
      version := "0.1.7",
      ScriptedPlugin.scriptedSettings,
      ScriptedPlugin.sbtTestDirectory := baseDirectory.value / "sbt-test",
      ScriptedPlugin.scriptedLaunchOpts += "-Dplugin.version=" + version.value,
      ScriptedPlugin.scriptedLaunchOpts += "-Dplugin.scalaVersion=" + dottyVersion,
     // By default scripted tests use $HOME/.ivy2 for the ivy cache. We need to override this value for the CI.
      ScriptedPlugin.scriptedLaunchOpts ++= ivyPaths.value.ivyHome.map("-Dsbt.ivy.home=" + _.getAbsolutePath).toList,
      ScriptedPlugin.scripted := ScriptedPlugin.scripted.dependsOn(Def.task {
        val x0 = (publishLocal in `dotty-sbt-bridge-bootstrapped`).value
        val x1 = (publishLocal in `dotty-interfaces`).value
        val x2 = (publishLocal in `dotty-compiler-bootstrapped`).value
        val x3 = (publishLocal in `dotty-library-bootstrapped`).value
        val x4 = (publishLocal in `scala-library`).value
        val x5 = (publishLocal in `scala-reflect`).value
        val x6 = (publishLocal in `dotty-bootstrapped`).value // Needed because sbt currently hardcodes the dotty artifact
      }).evaluated
    )

  lazy val `vscode-dotty` = project.in(file("vscode-dotty")).
    settings(commonSettings).
    settings(
      EclipseKeys.skipProject := true,
      version := "0.1.3", // Keep in sync with package.json

      autoScalaLibrary := false,
      publishArtifact := false,
      includeFilter in unmanagedSources := NothingFilter | "*.ts" | "**.json",
      watchSources in Global ++= (unmanagedSources in Compile).value,
      compile in Compile := {
        val coursier = baseDirectory.value / "out/coursier"
        val packageJson = baseDirectory.value / "package.json"
        if (!coursier.exists || packageJson.lastModified > coursier.lastModified)
          runProcess(Seq("npm", "install"), wait = true, directory = baseDirectory.value)
        val tsc = baseDirectory.value / "node_modules" / ".bin" / "tsc"
        runProcess(Seq(tsc.getAbsolutePath, "--pretty", "--project", baseDirectory.value.getAbsolutePath), wait = true)

        // Currently, vscode-dotty depends on daltonjorge.scala for syntax highlighting,
        // this is not automatically installed when starting the extension in development mode
        // (--extensionDevelopmentPath=...)
        runProcess(codeCommand.value ++ Seq("--install-extension", "daltonjorge.scala"), wait = true)

        sbt.inc.Analysis.Empty
      },
      sbt.Keys.`package`:= {
        runProcess(Seq("vsce", "package"), wait = true, directory = baseDirectory.value)

        baseDirectory.value / s"dotty-${version.value}.vsix"
      },
      unpublish := {
        runProcess(Seq("vsce", "unpublish"), wait = true, directory = baseDirectory.value)
      },
      publish := {
        runProcess(Seq("vsce", "publish"), wait = true, directory = baseDirectory.value)
      },
      run := Def.inputTask {
        val inputArgs = spaceDelimited("<arg>").parsed
        val codeArgs = if (inputArgs.isEmpty) List((baseDirectory.value / "..").getAbsolutePath) else inputArgs
        val extensionPath = baseDirectory.value.getAbsolutePath
        val processArgs = List(s"--extensionDevelopmentPath=${extensionPath}") ++ codeArgs

        runProcess(codeCommand.value ++ processArgs, wait = true)
      }.dependsOn(compile in Compile).evaluated
    )


  lazy val publishSettings = Seq(
    publishMavenStyle := true,
    isSnapshot := version.value.contains("SNAPSHOT"),
    publishTo := Some(
      if (isSnapshot.value)
        Opts.resolver.sonatypeSnapshots
      else
        Opts.resolver.sonatypeStaging
    ),
    publishArtifact in Test := false,
    homepage := Some(url(dottyGithubUrl)),
    licenses += ("BSD New",
      url(s"$dottyGithubUrl/blob/master/LICENSE.md")),
    scmInfo := Some(
      ScmInfo(
        url(dottyGithubUrl),
        "scm:git:git@github.com:lampepfl/dotty.git"
      )
    ),
    developers := List(
      Developer(
        id = "odersky",
        name = "Martin Odersky",
        email = "martin.odersky@epfl.ch",
        url = url("https://github.com/odersky")
      ),
      Developer(
        id = "DarkDimius",
        name = "Dmitry Petrashko",
        email = "me@d-d.me",
        url = url("https://d-d.me")
      ),
      Developer(
        id = "smarter",
        name = "Guillaume Martres",
        email = "smarter@ubuntu.com",
        url = url("http://guillaume.martres.me")
      ),
      Developer(
        id = "felixmulder",
        name = "Felix Mulder",
        email = "felix.mulder@gmail.com",
        url = url("http://felixmulder.com")
      ),
      Developer(
        id = "liufengyun",
        name = "Liu Fengyun",
        email = "liufengyun@chaos-lab.com",
        url = url("http://chaos-lab.com")
      ),
      Developer(
        id = "nicolasstucki",
        name = "Nicolas Stucki",
        email = "nicolas.stucki@gmail.com",
        url = url("https://github.com/nicolasstucki")
      ),
      Developer(
        id = "OlivierBlanvillain",
        name = "Olivier Blanvillain",
        email = "olivier.blanvillain@gmail.com",
        url = url("https://github.com/OlivierBlanvillain")
      ),
      Developer(
        id = "biboudis",
        name = "Aggelos Biboudis",
        email = "aggelos.biboudis@epfl.ch",
        url = url("http://biboudis.github.io")
      ),
      Developer(
        id = "allanrenucci",
        name = "Allan Renucci",
        email = "allan.renucci@gmail.com",
        url = url("https://github.com/allanrenucci")
      ),
      Developer(
        id = "Duhemm",
        name = "Martin Duhem",
        email = "martin.duhem@gmail.com",
        url = url("https://github.com/Duhemm")
      )
    )
  )

  // Compile with dotty
  lazy val compileWithDottySettings = {
    inConfig(Compile)(inTask(compile)(Defaults.runnerTask) ++ Seq(
      // Compile with dotty
      fork in compile := true,

      compile := {
        val inputs = (compileInputs in compile).value
        import inputs.config._

        val s = streams.value
        val logger = s.log
        val cacheDir = s.cacheDirectory

        // Discover classpaths

        def cpToString(cp: Seq[File]) =
          cp.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)

        val compilerCp = Attributed.data((fullClasspath in (`dotty-compiler`, Compile)).value)
        val cpStr = cpToString(classpath ++ compilerCp)

        // List all my dependencies (recompile if any of these changes)

        val allMyDependencies = classpath filterNot (_ == classesDirectory) flatMap { cpFile =>
          if (cpFile.isDirectory) (cpFile ** "*.class").get
          else Seq(cpFile)
        }

        // Compile

        val cachedCompile = FileFunction.cached(cacheDir / "compile",
            FilesInfo.lastModified, FilesInfo.exists) { dependencies =>

          logger.info(
              "Compiling %d Scala sources to %s..." format (
              sources.size, classesDirectory))

          if (classesDirectory.exists)
            IO.delete(classesDirectory)
          IO.createDirectory(classesDirectory)

          val sourcesArgs = sources.map(_.getAbsolutePath()).toList

          /* run.run() below in doCompile() will emit a call to its
           * logger.info("Running dotty.tools.dotc.Main [...]")
           * which we do not want to see. We use this patched logger to
           * filter out that particular message.
           */
          val patchedLogger = new Logger {
            def log(level: Level.Value, message: => String) = {
              val msg = message
              if (level != Level.Info ||
                  !msg.startsWith("Running dotty.tools.dotc.Main"))
                logger.log(level, msg)
            }
            def success(message: => String) = logger.success(message)
            def trace(t: => Throwable) = logger.trace(t)
          }

          def doCompile(sourcesArgs: List[String]): Unit = {
            val run = (runner in compile).value
            run.run("dotty.tools.dotc.Main", compilerCp,
                "-classpath" :: cpStr ::
                "-d" :: classesDirectory.getAbsolutePath() ::
                options ++:
                sourcesArgs,
                patchedLogger) foreach sys.error
          }

          // Work around the Windows limitation on command line length.
          val isWindows =
            System.getProperty("os.name").toLowerCase().indexOf("win") >= 0
          if ((fork in compile).value && isWindows &&
              (sourcesArgs.map(_.length).sum > 1536)) {
            IO.withTemporaryFile("sourcesargs", ".txt") { sourceListFile =>
              IO.writeLines(sourceListFile, sourcesArgs)
              doCompile(List("@"+sourceListFile.getAbsolutePath()))
            }
          } else {
            doCompile(sourcesArgs)
          }

          // Output is all files in classesDirectory
          (classesDirectory ** AllPassFilter).get.toSet
        }

        cachedCompile((sources ++ allMyDependencies).toSet)

        // We do not have dependency analysis when compiling externally
        sbt.inc.Analysis.Empty
      }
    ))
  }

  lazy val commonDistSettings = Seq(
    packMain := Map(),
    publishArtifact := false,
    packGenerateMakefile := false,
    packExpandedClasspath := true,
    packResourceDir += (baseDirectory.value / "bin" -> "bin"),
    packArchiveName := "dotty-" + dottyVersion
  )

  lazy val dist = project.asDist(NonBootstrapped)
  lazy val `dist-bootstrapped` = project.asDist(Bootstrapped)
  lazy val `dist-optimised` = project.asDist(BootstrappedOptimised)

  implicit class ProjectDefinitions(val project: Project) extends AnyVal {

    // FIXME: we do not aggregate `bin` because its tests delete jars, thus breaking other tests
    def asDottyRoot(implicit mode: Mode): Project = project.withCommonSettings.
      aggregate(`dotty-interfaces`, dottyLibrary, dottyCompiler, dottyDoc, dottySbtBridgeReference).
      bootstrappedAggregate(`scala-library`, `scala-compiler`, `scala-reflect`, scalap, `dotty-language-server`).
      dependsOn(dottyCompiler).
      dependsOn(dottyLibrary).
      nonBootstrappedSettings(
        addCommandAlias("run", "dotty-compiler/run")
      )

    def asDottyCompiler(implicit mode: Mode): Project = project.withCommonSettings.
      dependsOn(`dotty-interfaces`).
      dependsOn(dottyLibrary).
      settings(dottyCompilerSettings)

    def asDottyLibrary(implicit mode: Mode): Project = project.withCommonSettings.
      settings(dottyLibrarySettings).
      bootstrappedSettings(
        // Needed so that the library sources are visible when `dotty.tools.dotc.core.Definitions#init` is called.
        scalacOptions in Compile ++= Seq("-sourcepath", (scalaSource in Compile).value.getAbsolutePath)
      )

    def asDottyDoc(implicit mode: Mode): Project = project.withCommonSettings.
      dependsOn(dottyCompiler, dottyCompiler % "test->test").
      settings(dottyDocSettings)

    def asDottySbtBridge(implicit mode: Mode): Project = project.withCommonSettings.
      dependsOn(dottyCompiler).
      settings(dottySbtBridgeSettings)

    def asDottyBench(implicit mode: Mode): Project = project.withCommonSettings.
      dependsOn(dottyCompiler).
      settings(commonBenchmarkSettings).
      enablePlugins(JmhPlugin)

    def asDist(implicit mode: Mode): Project = project.
      enablePlugins(PackPlugin).
      withCommonSettings.
      dependsOn(`dotty-interfaces`, dottyCompiler, dottyLibrary, dottyDoc).
      settings(commonDistSettings).
      bootstrappedSettings(target := baseDirectory.value / "target") // override setting in commonBootstrappedSettings

    def withCommonSettings(implicit mode: Mode): Project = project.settings(mode match {
      case NonBootstrapped => commonNonBootstrappedSettings
      case Bootstrapped => commonBootstrappedSettings
      case BootstrappedOptimised => commonOptimisedSettings
    })
  }

}
