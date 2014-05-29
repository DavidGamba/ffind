package com.gambaeng.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object OptionParser {

  val logger = LoggerFactory.getLogger(this.getClass.getName)

  type OptionMap = Map[Symbol, Any]
  type OptionMapBuilder = Map[String, Symbol]

  def getOptions(args: Array[String], option_map: OptionMapBuilder): Tuple2[OptionMap, Array[String]] = {
    logger.debug(s"""[getOptions] Received args: ${args.mkString(",")}""")
    logger.debug(s"""[getOptions] Received map:  $option_map""")
    parseOptions(args.toList, option_map)
  }

  implicit class OptionMapImprovements(val m: OptionMapBuilder) {
    def match_key(opt: String): String = {
      val s = m.keys.find(_.matches(s"""[^-]*$opt(\\|.*)?(=.)?""")).getOrElse("")
      logger.trace(s"match_key: $opt -> $s")
      s
    }
    def match_get(opt: String): Option[Symbol] = {
      val s = m.get(m.match_key(opt))
      logger.trace(s"match_get: $opt -> $s")
      s
    }
    def match_apply(opt: String): Symbol = {
      val s = m(m.match_key(opt))
      logger.trace(s"match_apply: $opt -> $s")
      s
    }

    // Check allows to stop checking for options, e.g. -- is passed.
    def is_option(opt: String, check: Boolean = true): Boolean = {
      val ret = check && m.match_key(opt).matches("^-.*")
      logger.trace(s"is_option: $opt -> $ret")
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
    logger.debug(s"""[parseOptions] args: $args""")
    logger.debug(s"""[parseOptions] options:   $options""")
    logger.debug(s"""[parseOptions] skip:      ${skip.mkString(",")}""")
    args match {
      // Empty list
      case Nil => Tuple2(options, skip)

      // Flags
      case opt :: tail if option_map.is_option(opt) && option_map.is_flag(opt) && option_map.match_get(opt) != None =>
        parseOptions(tail, option_map,
          skip = skip,
          options = options ++ Map(option_map.match_apply(opt) -> true))

      // Options with values
      case opt :: value :: tail if option_map.is_option(opt) && !option_map.is_flag(opt) && option_map.match_get(opt) != None =>
        parseOptions(tail, option_map,
          skip = skip,
          options = options ++ Map(option_map.match_apply(opt) -> option_map.cast_value(opt, value)))

      // Fail on unknown options
      case opt :: tail if !option_map.is_option(opt) && opt.startsWith("-") => {
        System.err.println(s"Unknown option: $opt")
        sys.exit(1)
      }

      // Skip extra arguments
      case opt :: tail if !option_map.is_option(opt) =>
        parseOptions(tail, option_map,
          options = options,
          skip = skip :+ opt)
    }
  }
}
