package com.gambaeng.ffind

import com.gambaeng.utils.OptionParser
import com.gambaeng.utils.OptionMap
import com.gambaeng.utils.FileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file._
import scala.collection.JavaConversions._
import scala.sys.process._
import scala.language.implicitConversions

object FFind {
  implicit def file2RichFile(file: File) = new FileUtils.RichFile(file)

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
        "--type=s"        -> 'type,
        "-f"              -> 'type_file,
        "-d"              -> 'type_dir,
        "-I"              -> 'ignore_binary,
        "--hidden"        -> 'hidden,
        "--vcs"           -> 'vcs,
        "--full|fullpath" -> 'fullpath,
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
    ffind(file_pattern, dir, options)( (filename, m) => {
        print(filename.getParent + "/")
        print(m.group("pre"))
        print(Console.RED + m.group("matched") + Console.RESET)
        print(m.group("post"))
        println()
      }
    )
  }

  def validate_options(options: OptionMap) {
    if (options.contains('type) &&
        options[String]('type) != "f" &&
        options[String]('type) != "d") {
      System.err.println("[ERROR] Wrong type defined. Only 'f' and 'd' supported.")
      sys.exit(1)
    }
  }

  def ffind(file_pattern: String, dir: File, options: OptionMap)(f: (File, scala.util.matching.Regex.Match) => Unit) {
    validate_options(options: OptionMap)
    val nameFilter = if(options.contains('case) && options('case))
        new util.matching.Regex(
          s"""^(.*?)($file_pattern)(.*)$$""", "pre", "matched", "post")
      else
        new util.matching.Regex(
          s"""(?i)^(.*?)($file_pattern)(.*)$$""", "pre", "matched", "post")
    logger.debug(s"regex: %s" + nameFilter.toString)
    FileUtils.get_matched_files(dir, nameFilter)( (filename, m) => {
      if ( (options.contains('type_file) && options('type_file)) ||
           (options.contains('type) && options[String]('type) == "f") ) {
        if (filename.isFile) {
          if(!(options.contains('ignore_binary) && options('ignore_binary) && filename.isBinary))
            f(filename, m)
        }
      } else if ( (options.contains('type_dir) && options('type_dir)) ||
                  (options.contains('type) && options[String]('type) == "d") ) {
        if (filename.isDirectory)
          f(filename, m)
      } else {
        f(filename, m)
      }

    })
  }
}
