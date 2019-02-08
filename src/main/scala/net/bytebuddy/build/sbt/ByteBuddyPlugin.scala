package net.bytebuddy.build.sbt

import sbt._
import sbt.Keys._
import sbt.inc._

import xsbti.compile.CompileResult
import xsbti.compile.analysis.Stamp

object ByteBuddyPlugin extends AutoPlugin {
  /** Plugin must be enabled on the project. See http://www.scala-sbt.org/0.13/tutorial/Using-Plugins.html */
  override def trigger: PluginTrigger = noTrigger

  /** All we need is Java. */
  override def requires: Plugins = plugins.JvmPlugin

  object autoImport {
    val byteBuddyEnabled = settingKey[Boolean]("Whether the Byte Buddy enhancer is enabled or not")
    val byteBuddySuffix = settingKey[String]("The method name suffix that is used when type's method need to be rebased.")
    val byteBuddyInitialization = settingKey[String]("The initializer used for creating a ByteBuddy instance and for applying a transformation.")
    val byteBuddyPackages = taskKey[Seq[String]]("The packages that should be searched for ByteBuddy to transform.")
    val byteBuddyPlugins = settingKey[Seq[String]]("Transformation specifications to apply during the plugin\'s execution.")
  }

  import autoImport._

  def scopedSettings: Seq[Setting[_]] = Seq(manipulateBytecode :=
    (Def.taskDyn {
      SbtByteBuddy.byteBuddyTransform(
        manipulateBytecode.value,
        dependencyClasspath.value,
        byteBuddyPackages.value)
    }).value)

  lazy val defaultSettings: Seq[Setting[_]] = Seq(
    byteBuddyEnabled := true,
    byteBuddySuffix := "",
    byteBuddyInitialization := net.bytebuddy.build.EntryPoint.Default.REBASE.name(),
    byteBuddyPackages := Seq())

  override def projectSettings: Seq[Setting[_]] = inConfig(Compile)(scopedSettings) ++ defaultSettings
}

object SbtByteBuddy {
  import sbt.internal.inc.Hash
  import sbt.internal.inc.LastModified
  import sbt.internal.inc.Analysis
  import sbt.internal.inc.Stamper

  import ByteBuddyPlugin.autoImport._

  import net.bytebuddy._
  import net.bytebuddy.build._
  import net.bytebuddy.pool.TypePool
  import net.bytebuddy.dynamic.ClassFileLocator
  import net.bytebuddy.dynamic.DynamicType
  import net.bytebuddy.dynamic.scaffold.inline.MethodNameTransformer
  import java.io.FilenameFilter

  val CLASS_FILE_EXTENSION = ".class"

  // This is replacement of old Stamp `Exists` representation
  private final val notPresent = "absent"

  def byteBuddyTransform(result: CompileResult, deps: Seq[Attributed[File]], packages: Seq[String]): Def.Initialize[Task[CompileResult]] = Def.task {
    implicit val logger: Logger = streams.value.log

    val enabled = byteBuddyEnabled.value
    if (!enabled) {
      logger.info("ByteBuddy Disabled")
      result
    } else {
      // val deps = dependencyClasspath.value
      val classes = classDirectory.value

      val initialization = byteBuddyInitialization.value
      val suffix = byteBuddySuffix.value
      val plugins = byteBuddyPlugins.value
      //val packages = byteBuddyPackages.value

      val classpath = deps.map(_.data.toURI.toURL).toArray :+ classes.toURI.toURL

      // Parent loader as this ClassLoader
      implicit val classLoader = new java.net.URLClassLoader(classpath, this.getClass.getClassLoader)

      // Configure log
      val logHandler = ByteBuddyLogHandler(logger)
      val processedFiles =
        try {
          processOutputDirectory(classes, suffix, packages, classpath.map(_.getFile), initialization, plugins)
        } finally {
          classLoader.close
          logHandler.reset
        }

      if (processedFiles.isEmpty) {
        logger.verbose("No transformed files")
        result
      } else {
        val analysis = result.analysis.asInstanceOf[Analysis]
        val allProducts = analysis.relations.allProducts

        /**
         * Updates stamp of product (class file) by preserving the type of a passed stamp.
         * This way any stamp incremental compiler chooses to use to mark class files will
         * be supported.
         */
        def updateStampForClassFile(classFile: File, stamp: Stamp): Stamp = stamp match {
          //case _: Exists => sbt.internal.inc.Stamp.exists(classFile)
          case _: LastModified => Stamper.forLastModified(classFile)
          case _: Hash => Stamper.forHash(classFile)
        }
        // Since we may have modified some of the products of the incremental compiler, that is, the compiled template
        // classes and compiled Java sources, we need to update their timestamps in the incremental compiler, otherwise
        // the incremental compiler will see that they've changed since it last compiled them, and recompile them.
        val updatedAnalysis = analysis.copy(stamps = processedFiles.foldLeft(analysis.stamps) {
          (stamps, classFile) =>
            val existingStamp = stamps.product(classFile)
            if (existingStamp.writeStamp == notPresent) {
              throw new java.io.IOException("Tried to update a stamp for class file that is not recorded as "
                + s"product of incremental compiler: $classFile")
            }
            stamps.markProduct(classFile, updateStampForClassFile(classFile, existingStamp))
        })

        result.withAnalysis(updatedAnalysis).withHasModified(true)
      }
    }
  }

  def processOutputDirectory(
    root: File,
    suffix: String,
    filters: Seq[String],
    classPath: Array[String],
    initialization: String,
    pluginNames: Seq[String])(implicit classLoader: java.lang.ClassLoader, logger: Logger): Seq[File] = {

    if (!root.isDirectory()) {
      throw new java.io.IOException("Target location does not exist or is no directory: " + root)
    }

    val plugins = pluginNames.map(pluginName => {
      try {
        val loadedPlugin = classLoader.loadClass(pluginName).newInstance().asInstanceOf[net.bytebuddy.build.Plugin]

        logger.verbose(s"Resolved transformation plugin: $pluginName")
        loadedPlugin
      } catch {
        case ex: Exception => throw new IllegalStateException(s"Cannot create plugin: $pluginName", ex)
      }
    })

    val entryPoint = getEntryPoint(initialization)
    val transformedFiles = transform(root, suffix, filters, entryPoint, classPath, plugins)
    
    plugins.foreach(plugin => {
      try {
        plugin.close()
      } catch {
        case ex: java.io.IOException => throw new IllegalStateException(s"Cannot close plugin: " + plugin.getClass.getName(), ex)
      }
    })
    
    transformedFiles
  }

  def getEntryPoint(entryPoint: String)(implicit classLoader: java.lang.ClassLoader, logger: Logger): EntryPoint = {
    if (entryPoint == null || entryPoint.isEmpty()) {
      throw new java.io.IOException("Entry point name is not defined")
    }
    for (defaultEntryPoint <- EntryPoint.Default.values()) {
      if (entryPoint.equals(defaultEntryPoint.name())) {
        return defaultEntryPoint
      }
    }
    try {
      val loadedEntryPoint = classLoader.loadClass(entryPoint).asInstanceOf[EntryPoint]

      logger.verbose(s"Resolved entry point: $entryPoint")
      loadedEntryPoint
    } catch {
      case exception: Exception => throw new IllegalStateException(s"Cannot create entry point: $entryPoint", exception)
    }
  }

  /*
   * Return Filenames that is modified
   */
  def transform(
    root: File,
    suffix: String,
    filters: Seq[String],
    entryPoint: EntryPoint,
    classPath: Array[String],
    plugins: Seq[Plugin])(implicit logger: Logger): Seq[File] = {
    val classFileLocators = scala.collection.mutable.ListBuffer.empty[ClassFileLocator]
    classFileLocators += new ClassFileLocator.ForFolder(root)
    for (target <- classPath) {
      val artifact = new File(target)
      if (artifact.isFile()) {
        classFileLocators += ClassFileLocator.ForJarFile.of(artifact)
      } else {
        classFileLocators += new ClassFileLocator.ForFolder(artifact)
      }
    }

    import scala.collection.JavaConverters._
    import net.bytebuddy.ClassFileVersion

    val classFileLocator = new ClassFileLocator.Compound(classFileLocators.asJava)
    try {
      val typePool = new TypePool.Default.WithLazyResolution(
        new TypePool.CacheProvider.Simple(),
        classFileLocator,
        TypePool.Default.ReaderMode.FAST,
        TypePool.ClassLoading.ofBootLoader())

      logger.debug(s"Processing class files located in in: $root")
      val byteBuddy =
        try {
          entryPoint.byteBuddy(ClassFileVersion.ofThisVm())
        } catch {
          case throwable: Throwable => throw new java.io.IOException("Cannot create Byte Buddy instance", throwable)
        }

      val methodNameTransformer =
        if (suffix == null || suffix.isEmpty()) {
          MethodNameTransformer.Suffixing.withRandomSuffix()
        } else {
          new MethodNameTransformer.Suffixing(suffix)
        }

      val pathFinder: PathFinder =
        if (filters.isEmpty) {
          PathFinder(root)
        } else {
          filters.map(pack => {
            val path = pack.replace('.', '/')

            val pathFinder =
              if (path.endsWith("*")) {
                // Search by wild card
                ((root / path.substring(0, path.length - 1)) ** "*")
              } else {
                PathFinder(root / path)
              }

            pathFinder.filter { file => file.isFile() && file.getName().endsWith(CLASS_FILE_EXTENSION) }
          }).reduce((p1, p2) => p1 +++ p2)
        }

      pathFinder.get.map(file => {
        logger.debug("File: " + file)

        if (processClassFile(
          root,
          root.toURI().relativize(file.toURI()).toString(),
          byteBuddy,
          entryPoint,
          methodNameTransformer,
          classFileLocator,
          typePool,
          plugins)) {
          Option(file)
        } else {
          None
        }
      }).flatten
    } finally {
      classFileLocator.close()
    }
  }

  def processClassFile(
    root: File,
    file: String,
    byteBuddy: ByteBuddy,
    entryPoint: EntryPoint,
    methodNameTransformer: MethodNameTransformer,
    classFileLocator: ClassFileLocator,
    typePool: TypePool,
    plugins: Seq[Plugin])(implicit logger: Logger): Boolean = {

    val typeName = file.replace('/', '.').substring(0, file.length() - CLASS_FILE_EXTENSION.length)
    logger.verbose(s"Processing class file: $typeName")
    val typeDescription = typePool.describe(typeName).resolve()

    var builder: DynamicType.Builder[_] = null
    try {
      builder = entryPoint.transform(typeDescription, byteBuddy, classFileLocator, methodNameTransformer)
    } catch {
      case throwable: Throwable => throw new IllegalStateException(s"Cannot transform type: $typeName", throwable)
    }

    var transformed = false
    for (plugin <- plugins) {
      try {
        if (plugin.matches(typeDescription)) {
          builder = plugin.apply(builder, typeDescription, classFileLocator)
          transformed = true
        }
      } catch {
        case throwable: Throwable => throw new IllegalStateException(s"Cannot apply $plugin on $typeName", throwable)
      }
    }
    if (transformed) {
      logger.info(s"Transformed type: $typeName")
      val dynamicType = builder.make()

      import scala.collection.JavaConverters._
      for (entry <- dynamicType.getLoadedTypeInitializers().asScala) {
        if (entry._2.isAlive()) {
          throw new IllegalStateException("Cannot apply live initializer for " + entry._1)
        }
      }
      try {
        dynamicType.saveIn(root)

        true
      } catch {
        case exception: java.io.IOException => throw new IllegalStateException(s"Cannot save $typeName in $root", exception)
      }
    } else {
      logger.debug(s"Skipping non-transformed type: $typeName")

      false
    }
  }

}

