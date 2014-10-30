package com.gambaeng.ffind

import com.gambaeng.utils.OptionParser
import com.gambaeng.utils.OptionMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.nio.file._
import scala.collection.JavaConversions._
import scala.sys.process._
import scala.language.implicitConversions

object FFind {
  implicit def file2RichFile(file: File) = new RichFile(file)

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
    get_matched_files(dir, nameFilter)( (filename, m) => {
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

  class RichFile(val file: File) {
    /**
     *  Guess whether given file is binary. Just checks for anything under 0x09.
     *  Adapted from: http://stackoverflow.com/a/13533390/1601989
     */
    def isBinary: Boolean = {
        val in = new FileInputStream(file)
        val size = if(in.available() > 1024) 1024 else in.available()
        val data = new Array[Byte](size)
        in.read(data)
        in.close()

        var ascii = 0
        var other = 0

        for( i <- 0 until data.length) {
            val b: Byte = data(i)
            if( b < 0x09 ) return true

            if( b == 0x09 || b == 0x0A || b == 0x0C || b == 0x0D ) ascii+=1
            else if( b >= 0x20 && b <= 0x7E ) ascii+=1
            else other+=1
        }

        if( other == 0 ) return false

        100 * other / (ascii + other) > 95
    }
  }
}
