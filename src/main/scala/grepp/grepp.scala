package com.gambaeng.grepp

import com.gambaeng.utils.OptionParser
import com.gambaeng.utils.OptionMap
import com.gambaeng.utils.FileUtils
import com.gambaeng.ffind.FFind
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file._
import scala.collection.JavaConversions._
import scala.sys.process._
import java.io.FileWriter
import java.io.BufferedWriter
import java.io.IOException

object Grepp {

  val logger = LoggerFactory.getLogger(this.getClass.getName)
  val version = "0.1-dev"

  val help_string = """Usage:

  grepp <pattern> [[<dir>] [<file_pattern>]]
        [-c] [-l] [-I] [-n]
        [-r *replace* [-f|--force]]
        [--color *never*|*auto*|*always*]
        [--full|--full-path] [--hidden] [--vcs] [--fc|file-case]

  Not implemented
        [-v | --ignore *file_pattern*]
        [--spacing]
        [--pp|--prerocessor *program* --ppo *program options after filename*]

  grepp [--help] # shows extended help

  grepp [-h |-?] # shows short help
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
    logger.debug(s"grepp version $version")
    logger.debug(s"grepp ${args.mkString(" ")}")
    val (options, remaining) = OptionParser.parse(args,
      Map(
        "--help=p"        -> { () => show_man_page(); sys.exit(1) },
        "-h=p"            -> { () => System.err.println(help_string); sys.exit(1) },
        "--version=p"     -> { () => System.err.println(s"grepp version $version"); sys.exit(1) },
        "-c|--case"       -> 'case,
        "-I"              -> 'binary,
        "-n"              -> 'number,
        "-f|--force"      -> 'force,
        "-r=s"            -> 'replace,
        "--hidden"        -> 'show_hidden,
        "--vcs"           -> 'vcs,
        "--full|fullpath" -> 'fullpath,
        "--fc|file-case"  -> 'file_case,
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
    // grepp pattern
    val (pattern, file_pattern, dir) = if (remaining.size == 1) {
      // TODO: Have it so it searches the full git repo by default
      (remaining(0), "", new File("./"))
    // grepp pattern dir
    // or
    // grepp pattern file_pattern
    } else if (remaining.size == 2) {
      // TODO: Have it so it searches the full git repo by default
      val f = new File(remaining(1))
      if(f.exists && f.isDirectory)
        (remaining(0), "", f)
      else
        (remaining(0), remaining(1), new File("./"))
    // grepp pattern dir file_pattern
    } else {
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
    val ffind_options = OptionMap(
      'type_file -> true,
      'ignore_binary -> true,
      'show_hidden -> (options.contains('show_hidden) && options('show_hidden)),
      'case -> (options.contains('file_case) && options('file_case)),
      'vcs -> (options.contains('vcs) && options('vcs))
    )

    // TODO: Refactor this code repetition before it gets out of control
    if(options.contains('replace)) {
      FFind.ffind(file_pattern, dir, ffind_options)( (filename, m) => {
        val tmp = new File("/tmp/grepp")
        val fw = new FileWriter(tmp, false)
        val bw = new BufferedWriter(fw)
        FileUtils.match_lines_in_file(filename, patternFilter)( (line, matches) => {
          val f = new File(filename.getAbsolutePath).getParent.replace("/./", "/")
          print(Console.MAGENTA)
          if (filename.getParent != null) {
            if(options.contains('fullpath) && options('fullpath))
              print(f + "/")
            else
              print(filename.getParent + "/")
          }
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
        if(options.contains('force) && options('force)) {
          logger.debug("copy " + tmp.toPath + " " + filename.toPath)
          Files.copy(tmp.toPath, filename.toPath, StandardCopyOption.REPLACE_EXISTING)
        }
      }
      )
    }
    else {
      FFind.ffind(file_pattern, dir, ffind_options)( (filename, m) => {
          FileUtils.match_lines_in_file(filename, patternFilter)( (line, matches) => {
            val f = new File(filename.getAbsolutePath).getParent.replace("/./", "/")
            print(Console.MAGENTA)
            if (filename.getParent != null) {
              if(options.contains('fullpath) && options('fullpath))
                print(f + "/")
              else
                print(filename.getParent + "/")
            }
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

}
