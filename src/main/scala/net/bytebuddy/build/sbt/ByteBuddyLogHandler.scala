package net.bytebuddy.build.sbt

import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.{ Logger => JLogger }
import java.util.logging.SimpleFormatter
import sbt.Logger

object ByteBuddyLogHandler {
  def apply(log: Logger): ByteBuddyLogHandler = {
    val logger = JLogger.getLogger("net.bytebuddy")

    val handler = new ByteBuddyLogHandler(logger, log, logger.getUseParentHandlers)
    handler.setFormatter(new SimpleFormatter())

    try {
      logger.setUseParentHandlers(false)
      logger.addHandler(handler)
    } catch {
      case exception: SecurityException => {
        log.warn("Cannot configure Byte Buddy logging: " + exception)
      }
    }

    handler
  }
}

class ByteBuddyLogHandler(
    val logger: JLogger, val log: Logger,
    val useParentHandlers: Boolean) extends Handler {

  /**
   * Resets the configuration to its original state.
   */
  def reset() {
    try {
      logger.setUseParentHandlers(useParentHandlers)
      logger.removeHandler(this)
    } catch {
      case exception: SecurityException => {
        log.warn("Cannot configure Byte Buddy logging: " + exception)
      }
    }
  }

  override def publish(record: LogRecord) {
    log.debug(getFormatter().format(record))
  }

  override def flush() {
    /* do nothing */
  }

  override def close() {
    /* do nothing */
  }
}
