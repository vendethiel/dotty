package dotty.tools.sbtplugin

import sbt._
import sbt.Keys._
import java.io.{File => JFile}
import java.lang.ProcessBuilder

import com.fasterxml.jackson.databind.ObjectMapper

private object IDE {
  // our version of http://stackoverflow.com/questions/25246920
  implicit class RichSettingKey[A](key: SettingKey[A]) {
    def gimme(implicit pr: ProjectRef, bs: BuildStructure, s: State): A =
      gimmeOpt getOrElse { fail(s"Missing setting: ${key.key.label}") }
    def gimmeOpt(implicit pr: ProjectRef, bs: BuildStructure, s: State): Option[A] =
      key in pr get bs.data
  }

  implicit class RichTaskKey[A](key: TaskKey[A]) {
    def run(implicit pr: ProjectRef, bs: BuildStructure, s: State): A =
      runOpt.getOrElse { fail(s"Missing task key: ${key.key.label}") }
    def runOpt(implicit pr: ProjectRef, bs: BuildStructure, s: State): Option[A] =
      EvaluateTask(bs, key, s, pr).map(_._2) match {
        case Some(Value(v)) => Some(v)
        case _              => None
      }

    def forAllProjects(state: State, projects: Seq[ProjectRef]): Task[Map[ProjectRef, A]] = {
      val tasks = projects.flatMap(p => key.in(p).get(Project.structure(state).data).map(_.map(it => (p, it))))
      std.TaskExtra.joinTasks(tasks).join.map(_.toMap)
    }
  }

  val builtInTestPhases = Set(Test, IntegrationTest)

  def projectData(
    project: ResolvedProject/*,
    updateReport: UpdateReport,
    updateClassifiersReport: UpdateReport*/
  )(
    implicit
    projectRef: ProjectRef,
    buildStruct: BuildStructure,
    state: State
  ) = {
    val testPhases = {
      for {
        phase <- ivyConfigurations.gimme.toList
        if phase.isPublic
        if builtInTestPhases(phase) || builtInTestPhases.intersect(phase.extendsConfigs.toSet).nonEmpty
      } yield phase
    }

    def sourcesFor(config: Configuration) = {
      (managedSources in config).runOpt
      (managedSourceDirectories in config).gimmeOpt.getOrElse(Seq()) ++
        (unmanagedSourceDirectories in config).gimmeOpt.getOrElse(Seq())
    }

    def configFilter(config: Configuration): ConfigurationFilter = {
      val c = config.name.toLowerCase
      if (sbtPlugin.gimme) configurationFilter("provided" | c)
      else configurationFilter(c)
    }
    // needs to take configurations into account
    // https://github.com/ensime/ensime-sbt/issues/247
    // val deps = project.dependencies.map(_.project.project).map { n => EnsimeProjectId(n, "compile") }

    val compilerVersion = scalaVersion.gimme

    def configDataFor(config: Configuration) = {
      val id = s"${project.id}/${config.name}"

      val sources = config match {
        case Compile => sourcesFor(Compile) ++ sourcesFor(Provided) ++ sourcesFor(Optional)
        case _       => sourcesFor(config)
      }

      val scalacArgs = Seq()//(scalacOptions in config).run
      val depCp = Attributed.data((dependencyClasspath in config).runOpt.getOrElse(Seq()))
      val target = (classDirectory in config).gimme

      new IDEConfig(
        id,
        compilerVersion.replace("-nonbootstrapped", ""), // The language server is only published bootstrapped
        sources.toArray,
        scalacArgs.toArray,
        depCp.toArray,
        target)
    }

    // TODO: cross support (require equivalent to ++)
    if (!compilerVersion.startsWith("0."))
      Nil
    else
      (Compile :: testPhases).map(configDataFor)
  }

  private def compileProject(project: ResolvedProject)
    (implicit projectRef: ProjectRef, buildStruct: BuildStructure, state: State) = {
    val testPhases = {
      for {
        phase <- ivyConfigurations.gimme.toList
        if phase.isPublic
        if builtInTestPhases(phase) || builtInTestPhases.intersect(phase.extendsConfigs.toSet).nonEmpty
      } yield phase
    }
    (Compile :: testPhases).foreach { config =>

      val compilerVersion = (scalaVersion in config).gimme
      if (compilerVersion.startsWith("0."))
        (compile in config).runOpt
    }
  }

  def writeConfig(s: State): Unit = {
    val struct = Project.structure(s)
    val refs = struct.allProjectRefs
    val configs = refs flatMap { ref =>
      // println(managedSources.in(Compile).in(ref).get(struct.data))
      val proj = Project.getProjectForReference(ref, struct).get
      projectData(proj)(ref, struct, s)
    }

    val mapper = new ObjectMapper
    mapper.writerWithDefaultPrettyPrinter()
      .writeValue(new File(".dotty-ide.json"), configs.toArray)
  }
  def compileForIDE(s: State): Unit = {
    val struct = Project.structure(s)
    val refs = struct.allProjectRefs
    val configs = refs.foreach { ref =>
      val proj = Project.getProjectForReference(ref, struct).get
      compileProject(proj)(ref, struct, s)
    }
  }
}

object DottyIDEPlugin extends AutoPlugin {
  object autoImport {
    val runCode = taskKey[Unit]("Run Visual Studio Code on this project")
  }

  import autoImport._

  override def requires: Plugins = plugins.JvmPlugin
  override def trigger = allRequirements


  override def projectSettings: Seq[Setting[_]] = Seq(
    commands ++= Seq(
      Command.command("configureIDE")(state => { IDE.writeConfig(state); state }),
      Command.command("compileForIDE")(state => { IDE.compileForIDE(state); state })
    )
  )
  override def buildSettings: Seq[Setting[_]] = Seq(
    runCode := {
      val exitCode = new ProcessBuilder("code", "--install-extension", "lampepfl.dotty")
        .inheritIO()
        .start()
        .waitFor()
      if (exitCode != 0)
        throw new FeedbackProvidedException {
          override def toString = "Installing the Dotty support for VSCode failed"
        }

      new ProcessBuilder("code", baseDirectory.value.getAbsolutePath)
        .inheritIO()
        .start()
    }
  )
}
