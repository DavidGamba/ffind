package com.gambaeng.ffind

import com.gambaeng.utils.OptionParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file._
import scala.collection.JavaConversions._

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

  def run(args: Array[String]) {
    logger.info(s"ffind ${args.mkString(" ")}")
    val (options, remaining) = OptionParser.getOptions(args,
      Map(
        "--help" -> 'man,
        "-h" -> 'help,
        "--version" -> 'version,
        "-c|--case" -> 'case,
        "-f" -> 'type_file,
        "-d" -> 'type_dir,
        "--hidden" -> 'hidden,
        "--vcs" -> 'vcs,
        "--full|fullpath" -> 'fullpath,
        "--type=s" -> 'type,
        "--color=s" -> 'color,
        "--int=i" -> 'int
      ))
    if (options isDefinedAt 'help) {
      System.err.println(help_string)
      sys.exit(1)
    }
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
      Tuple2(remaining(1), new File(remaining(0)))
    } else {
      Tuple2(remaining(0), new File("./"))
    }

    logger.debug(s"file_pattern: $file_pattern")
    logger.debug(s"dir: $dir")
    val nameFilter = s"""^(.*?)($file_pattern)(.*)$$"""
    getFileTree(dir).foreach(
      filename => if (filename.getName.matches(nameFilter))
        println(filename)
    )
  }
}
