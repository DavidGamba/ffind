package com.gambaeng.ffind

import com.gambaeng.utils.OptionParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file._
import scala.collection.JavaConversions._
import scala.sys.process._

object FFind {

  val logger = LoggerFactory.getLogger(this.getClass.getName)

  val help_string = """Usage:
        ffind [<dir>] <file_pattern>
              [ [-f] [-d] [--type <f|d>] ]
              [--full|--full-path]
              [--hidden]
              [--vcs]
              [--color <never|auto|always>]

        ffind [-h |-?]       # shows short help
        ffind [--help]       # shows the man page

"""

  def getFileTree(f: File): Stream[File] = f #:: Option(f.listFiles()).toStream.flatten.flatMap(getFileTree)

  def show_man_page() = {
    val os = System.getProperty("os.name")
    if (os == "Linux") {
      Seq("man", "-l", "ffind.1").!
    }
    else if(os == "NT") {
      // groff -Tascii -mm your_file | more
      Seq("groff", "-Tascii", "ffind.1").!
    }
  }

  def run(args: Array[String]) {
    logger.info(s"ffind ${args.mkString(" ")}")
    val (options, remaining) = OptionParser.parse(args,
      Map(
        "--help"          -> { () => show_man_page(); sys.exit(1) },
        "-h"              -> { () => System.err.println(help_string); sys.exit(1) },
        "--version"       -> 'version,
        "-c|--case"       -> 'case,
        "-f"              -> 'type_file,
        "-d"              -> 'type_dir,
        "--hidden"        -> 'hidden,
        "--vcs"           -> 'vcs,
        "--full|fullpath" -> 'fullpath,
        "--type=s"        -> 'type,
        "--color=s"       -> 'color
      )
    )
    logger.debug(s"options: $options")
    logger.debug(s"""remaining: ${remaining.mkString(", ")}""")

    if (remaining.size < 1) {
      System.err.println("[ERROR] Missing file_pattern!")
      sys.exit(1)
    }
    val (file_pattern, dir) = if (remaining.size >= 2) {
      // If given dir has an absolute path then print result as absolute path
      if (remaining(0).startsWith("/")) {
        // options('fullpath -> true)
      }
      (remaining(1), new File(remaining(0)))
    } else {
      // TODO: Have it so it searches the full git repo by default
      (remaining(0), new File("./"))
    }

    logger.debug(s"file_pattern: $file_pattern")
    logger.debug(s"dir: $dir")
    val nameFilter = if(options.contains('case) && options('case))
        new util.matching.Regex(
          s"""^(.*?)($file_pattern)(.*)$$""", "pre", "matched", "post")
      else
        new util.matching.Regex(
          s"""(?i)^(.*?)($file_pattern)(.*)$$""", "pre", "matched", "post")

    logger.debug(s"regex: %s" + nameFilter.toString)
    get_matched_files(dir, nameFilter)( (filename, m) => {
        print(filename.getParent + "/")
        print(m.group("pre"))
        print(Console.RED + m.group("matched") + Console.RESET)
        print(m.group("post"))
        println()
      }
    )
  }

  def get_matched_files(dir: File, nameFilter: util.matching.Regex)(f: (File, scala.util.matching.Regex.Match) => Unit) {
    match_files(dir, nameFilter)( (filename, m) =>
      m match {
        case Some(m) => {
          f(filename, m)
        }
        case None => {}
      }
    )
  }

  def match_files(dir: File, nameFilter: util.matching.Regex)(f: (File, Option[scala.util.matching.Regex.Match]) => Unit) {
    getFileTree(dir).foreach{ filename =>
      f(filename, nameFilter findFirstMatchIn filename.getName)
    }
  }
}
