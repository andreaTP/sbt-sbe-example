import sbt._
import Keys._

import scala.sys.process._
import scala.io.Source
import java.net.URL
import java.io.File

object SbePlugin extends AutoPlugin {

  object autoImport {
    val sbeRepo = settingKey[String]("URL for fetching SBE jar")
    val sbeVersion = settingKey[String]("Version of SBE to use")

    // Mapping of SBE options
    // https://github.com/real-logic/simple-binary-encoding/blob/6175bfa971b0a61bf4c98c003dd979450217629a/sbe-tool/src/main/java/uk/co/real_logic/sbe/SbeTool.java#L71
    val generateIr = settingKey[Boolean]("SBE generate IR")
    val targetLanguage = settingKey[String]("SBE target Language")
    val targetNamespace = settingKey[String]("SBE target Namespace")
    val outputDir = settingKey[File]("SBE output directory")
    val errorLog = settingKey[Boolean]("SBE error log")

    // To add not re-binded properties
    val unmanagedCommandLine = settingKey[String]("SBE additional parameters for command line")

    val inputDir = settingKey[File]("SBE input files")

    val fetchJar = taskKey[File]("Fetch SBE Jar from the internet")
    val sbeGenerate = taskKey[Seq[File]]("Generate SBE code")
  }

  import autoImport._
  override def trigger = noTrigger

  override lazy val globalSettings = Seq(
    sbeRepo := "https://repo1.maven.org/maven2",
    sbeVersion := "1.8.8",
    generateIr := true,
    targetLanguage := "Java",
    targetNamespace := "sbe",
    errorLog := true,
    unmanagedCommandLine := ""
  )

  override lazy val projectSettings = Seq(
    outputDir := {
      (Compile / sourceManaged).value
    },
    inputDir := {
      (Compile / sourceDirectory).value / "sbe"
    },

    fetchJar := {
      val jar = new URL(s"${sbeRepo.value}/uk/co/real-logic/sbe-all/${sbeVersion.value}/sbe-all-${sbeVersion.value}.jar")

      val destDir = (Compile / target).value / s"sbe_${sbeVersion.value}"

      IO.createDirectory(destDir)

      val destFile = (destDir / "sbe.jar")

      if (!destFile.exists) {
        jar #> destFile !
      }

      destFile
    },

    sbeGenerate := {
      val sbe = fetchJar.value

      val props = Seq(
        s"-Dsbe.generate.ir=${generateIr.value}",
        s"-Dsbe.target.language=${targetLanguage.value}",
        s"-Dsbe.target.namespace=${targetNamespace.value}",
        s"-Dsbe.output.dir=${outputDir.value.getAbsolutePath}",
        s"-Dsbe.errorLog=${errorLog.value}",
        unmanagedCommandLine.value
      ).mkString(" ")

      IO.listFiles(inputDir.value).foreach { f =>
        val fileName = f.getAbsolutePath
        if (fileName.endsWith("xml"))
          s"java $props -jar ${sbe.getAbsolutePath} ${fileName}" !
      }

      IO.listFiles(outputDir.value)
    },

    // managed source directories doesn't compile Java files ...
    (Compile / unmanagedSourceDirectories) += (Compile / outputDir).value,

    libraryDependencies += "uk.co.real-logic" % "sbe-all" % sbeVersion.value
  )
}
