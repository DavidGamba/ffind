package com.gambaeng.utils

// TODO: Remove this?
import org.slf4j.Logger
import org.slf4j.LoggerFactory
// Imports to keep
import java.io.File
import java.io.FileInputStream
import scala.io.Source

object FileUtils {
  // Returns a Stream of File under, and including, the given File.
  def getFileTree(f: File, ignore_starts: List[String] = List(), ignore_equals: List[String] = List()): Stream[File] = {
    if(!f.exists)
      return Stream()
    f #:: Option(f.listFiles()).toStream.flatten.flatMap( x => {
      Option(
        if(ignore_starts.filter( e => x.getName.startsWith(e) ).size >= 1) {
          null
        } else if(ignore_equals.filter( e => e == x.getName ).size >= 1) {
          null
        } else {
          getFileTree(x, ignore_starts, ignore_equals)
        }
      ).toStream.flatten
    })
  }

  // Given a File (dir), a Regex, and a function of File and Regex.Match, runs the function on all files under the File.
  def match_files(dir: File, nameFilter: util.matching.Regex, ignore_starts: List[String] = List(), ignore_equals: List[String] = List())(f: (File, Option[scala.util.matching.Regex.Match]) => Unit) {
    FileUtils.getFileTree(dir, ignore_starts, ignore_equals).foreach{ filename =>
      f(filename, nameFilter findFirstMatchIn filename.getName)
    }
  }
  // Given a File (dir), a Regex, and a function of File and Regex.Match, runs the function on the files under File that match the given Regex.
  def get_matched_files(dir: File, nameFilter: util.matching.Regex, ignore_starts: List[String] = List(), ignore_equals: List[String] = List())(f: (File, scala.util.matching.Regex.Match) => Unit) {
    match_files(dir, nameFilter, ignore_starts, ignore_equals)( (filename, m) =>
      m match {
        case Some(m) => {
          f(filename, m)
        }
        case None => {}
      }
    )
  }

  /**
   * Given a file and a regular expression, a required function and an optional function.
   * Passes the iterator of matches to the required function.
   * Passes the lines without matches to the optional function.
   */
  def match_lines_in_file(file: File, pattern: util.matching.Regex)
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

  class RichFile(val file: File) {
    /**
     *  Guess whether given file is binary. Just checks for anything under 0x09.
     *  Adapted from: http://stackoverflow.com/a/13533390/1601989
     */
    def isBinary: Boolean = {
        if (file.isDirectory)
          return false
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
