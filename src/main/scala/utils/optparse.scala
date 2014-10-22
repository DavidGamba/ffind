/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 DavidGamba
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.gambaeng.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object OptionParser {

  val logger = LoggerFactory.getLogger(this.getClass.getName)

  type OptionMap = Map[Symbol, Any]
  type OptionMapBuilder = Map[String, Any]

  def getOptions(args: Array[String], option_map: OptionMapBuilder): Tuple2[OptionMap, Array[String]] = {
    logger.debug(s"""[getOptions] Received args: ${args.mkString(",")}""")
    logger.debug(s"""[getOptions] Received map:  $option_map""")
    parseOptions(args.toList, option_map)
  }

  implicit class OptionMapImprovements(val m: OptionMapBuilder) {
    def match_key(opt: String): String = {
      val stripped_opt = if(opt.contains("=")) opt.split("=")(0) else opt
      val s = m.keys.find(_.matches(s"""[^-]*$stripped_opt(\\|.*)?(=.)?""")).getOrElse("")
      logger.trace(s"match_key: $opt -> $s")
      s
    }
    def match_get(opt: String): Option[Symbol] = {
      val s = m.get(m.match_key(opt))
      logger.trace(s"match_get: $opt -> $s")
      s.asInstanceOf[Option[Symbol]]
    }
    def match_get_function(opt: String): () => Unit = {
      val s = m.get(m.match_key(opt))
      logger.trace(s"match_get_function: $opt -> $s")
      s.get.asInstanceOf[() => Unit]
    }
    def match_apply(opt: String): Symbol = {
      val s = m(m.match_key(opt))
      logger.trace(s"match_apply: $opt -> $s")
      s.asInstanceOf[Symbol]
    }

    // Check allows to stop checking for options, e.g. -- is passed.
    def is_option(opt: String, check: Boolean = true): Boolean = {
      val ret = check && m.match_key(opt).matches("^-.*")
      logger.trace(s"is_option: $opt -> $ret")
      ret
    }

    // If the option definition has p for procedure it is a function
    def is_function(opt: String): Boolean = {
      val ret = m.match_key(opt).matches(".*=p$")
      logger.trace(s"is_function: $opt -> $ret")
      ret
    }

    // If the option definition doesn't have the '=' symbol, it is just a flag
    def is_flag(opt: String): Boolean = {
      val ret = !m.match_key(opt).matches(".*=.$")
      logger.trace(s"is_flag: $opt -> $ret")
      ret
    }

    def cast_value(opt: String, value: String): Any = {
      val key = m.match_key(opt)
      val ret = if (key.matches(".*=i$")) {
        logger.trace("toInt")
        value.toInt
      } else if (key.matches(".*=f$")) {
        logger.trace("toDouble")
        value.toDouble
      } else if (key.matches(".*=s$")) {
        logger.trace("string")
        value
      }
      val ret_type = ret.getClass
      logger.trace(s"cast_value: $opt, $value, type: $ret_type ")
      ret
    }
  }

  private def parseOptions(args: List[String],
                           option_map: OptionMapBuilder,
                           options: OptionMap = Map[Symbol, String](),
                           skip: Array[String] = Array[String]()): Tuple2[OptionMap, Array[String]] = {
    logger.trace(s"""[parseOptions] args:    $args""")
    logger.trace(s"""[parseOptions] options: $options""")
    logger.trace(s"""[parseOptions] skip:    ${skip.mkString(",")}""")
    args match {
      // Empty list
      case Nil => Tuple2(options, skip)

      // Stop on --
      case opt :: tail if opt == "--" => Tuple2(options, skip ++: tail.toArray)

      // Options with values after "=". e.g --opt=value
      case opt :: tail if option_map.is_option(opt) &&
                          !option_map.is_flag(opt) &&
                          !option_map.is_function(opt) &&
                          option_map.match_get(opt) != None &&
                          opt.contains("=") => {
        logger.debug(s"Argument $opt maps to an option with value.")
        parseOptions(tail, option_map,
          skip = skip,
          options = options ++ Map(option_map.match_apply(opt) -> option_map.cast_value(opt, opt.split("=")(1))))
      }

      // Flags
      case opt :: tail if option_map.is_option(opt) &&
                          option_map.is_flag(opt) &&
                          option_map.match_get(opt) != None => {
        logger.debug(s"Argument $opt maps to a flag.")
        parseOptions(tail, option_map,
          skip = skip,
          options = options ++ Map(option_map.match_apply(opt) -> true))
      }

      // Options with functions
      case opt :: tail if option_map.is_option(opt) &&
                          option_map.is_function(opt) &&
                          option_map.match_key(opt) != "" => {
        logger.debug(s"Argument $opt maps to a function call.")
        option_map.match_get_function(opt)()
        parseOptions(tail, option_map,
          skip = skip,
          options = options)
      }

      // Options with values
      case opt :: value :: tail if option_map.is_option(opt) &&
                                   !option_map.is_flag(opt) &&
                                   option_map.match_get(opt) != None => {
        logger.debug(s"Argument $opt maps to an option with value.")
        parseOptions(tail, option_map,
          skip = skip,
          options = options ++ Map(option_map.match_apply(opt) -> option_map.cast_value(opt, value)))
      }

      // Options with missing values
      case opt :: tail if option_map.is_option(opt) &&
                          !option_map.is_flag(opt) &&
                          option_map.match_get(opt) != None => {
        logger.debug(s"Argument $opt maps to an option with missing value.")
        Console.err.println(s"Option $opt requires an argument")
        parseOptions(tail, option_map,
          skip = skip,
          options = options)
      }

      // Warn on unknown options and ignore them
      case opt :: tail if !option_map.is_option(opt) && opt.startsWith("-") => {
        logger.debug(s"Argument $opt maps to an unknown option.")
        if (opt.contains("="))
          Console.err.println(s"""Unknown option: ${opt.split("=")(0)}""")
        else
          Console.err.println(s"Unknown option: $opt")
        parseOptions(tail, option_map,
          options = options,
          skip = skip)
      }

      // Skip extra arguments
      case opt :: tail if !option_map.is_option(opt) => {
        logger.debug(s"Argument $opt is not an option.")
        parseOptions(tail, option_map,
          options = options,
          skip = skip :+ opt)
      }
    }
  }
}
