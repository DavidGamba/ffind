package com.gambaeng.grepp

import com.gambaeng.utils.OptionParser
import com.gambaeng.utils.OptionMap
import com.gambaeng.ffind.FFind
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file._
import scala.collection.JavaConversions._
import scala.sys.process._
import scala.io.Source
import java.io.FileWriter
import java.io.BufferedWriter
import java.io.IOException

object Grepp {

  val logger = LoggerFactory.getLogger(this.getClass.getName)

  val help_string = """Usage:

  grepp [-c] [-l] [-I] [-n] *pattern* [[dir] *file_pattern*] [--fullpath]
        [-r *replace*]
        [--color *never*|*auto*|*always*]
        [-v | --ignore *file_pattern*]
        [--spacing]
        [--pp|--prerocessor *program* --ppo *program options after filename*]

  grepp [--help] # shows extended help

  grepp [-h |-?] # shows short help
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
    logger.info(s"grepp ${args.mkString(" ")}")
    val (options, remaining) = OptionParser.parse(args,
      Map(
        "--help"          -> { () => show_man_page(); sys.exit(1) },
        "-h"              -> { () => System.err.println(help_string); sys.exit(1) },
        "--version"       -> 'version,
        "-c|--case"       -> 'case,
        "-I"              -> 'binary,
        "-n"              -> 'number,
        "-f"              -> 'force,
        "-r=s"            -> 'replace,
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
      System.err.println("[ERROR] Missing pattern!")
      sys.exit(1)
    }
    val (pattern, file_pattern, dir) = if (remaining.size == 1) {
      // TODO: Have it so it searches the full git repo by default
      (remaining(0), ".*", new File("./"))
    } else if (remaining.size == 2) {
      // TODO: Have it so it searches the full git repo by default
      (remaining(0), remaining(1), new File("./"))
    } else {
      // If given dir has an absolute path then print result as absolute path
      if (remaining(1).startsWith("/")) {
        // options('fullpath -> true)
      }
      (remaining(0), remaining(2), new File(remaining(1)))
    }

    logger.debug(s"pattern:      $pattern")
    logger.debug(s"file_pattern: $file_pattern")
    logger.debug(s"dir:          $dir")
    val patternFilter = if(options.contains('case) && options('case))
        new util.matching.Regex(
          s"""(.*?)($pattern)""", "pre", "matched")
      else
        new util.matching.Regex(
          s"""(?i)(.*?)($pattern)""", "pre", "matched")
    logger.debug(s"regex: %s" + patternFilter.toString)
    val ffind_options = OptionMap('type_file -> true, 'ignore_binary -> true)

    // TODO: Refactor this code repetition before it gets out of control
    if(options.contains('replace)) {
      FFind.ffind(file_pattern, dir, ffind_options)( (filename, m) => {
        val tmp = new File("/tmp/grepp")
        val fw = new FileWriter(tmp, false)
        val bw = new BufferedWriter(fw)
        match_in_files(filename, patternFilter)( (line, matches) => {
          // filename
          print(Console.MAGENTA + filename.getParent + "/")
          print(m.group("pre"))
          print(Console.BLUE + Console.BOLD + m.group("matched") + Console.RESET)
          print(Console.MAGENTA + m.group("post") + Console.RESET + " : ")
          // line
          val after = for(l <- matches) {
            print(l.group("pre"))
            bw.write(l.group("pre"))
            print(Console.RED + l.group("matched") + Console.RESET)
            print(Console.GREEN + options[String]('replace) + Console.RESET)
            bw.write(options[String]('replace))
            if(!matches.hasNext) {
              println(l.after)
              bw.write(l.after.toString + "\n")
            }
          }
          })(line => {
            bw.write(line + "\n")
          })
        bw.close
        fw.close
        if(options.contains('force) && options('force))
          Files.copy(tmp.toPath, filename.toPath, StandardCopyOption.REPLACE_EXISTING)
      }
      )
    }
    else {
      FFind.ffind(file_pattern, dir, ffind_options)( (filename, m) => {
          match_in_files(filename, patternFilter)( (line, matches) => {
            // filename
            print(Console.MAGENTA + filename.getParent + "/")
            print(m.group("pre"))
            print(Console.BLUE + Console.BOLD + m.group("matched") + Console.RESET)
            print(Console.MAGENTA + m.group("post") + Console.RESET + " : ")
            // line
            val after = for(l <- matches) {
              print(l.group("pre"))
              print(Console.RED + l.group("matched") + Console.RESET)
              if(!matches.hasNext)
                println(l.after)
            }
          })
        }
      )
    }
  }

  /**
   * Given a file and a regular expression, a required function and an optional function.
   * Passes the iterator of matches to the required function.
   * Passes the lines without matches to the optional function.
   */
  def match_in_files(file: File, pattern: util.matching.Regex)
                    (f: (String, Iterator[scala.util.matching.Regex.Match]) => Unit)
                    (implicit p: (String) => Unit = { _ => Unit }) {
    for(line <- Source.fromFile(file).getLines()) {
      val matches = pattern.findAllMatchIn(line)
      if(matches.nonEmpty) {
        f(line, matches)
      }
      else {
        p(line)
      }
    }
  }
}
