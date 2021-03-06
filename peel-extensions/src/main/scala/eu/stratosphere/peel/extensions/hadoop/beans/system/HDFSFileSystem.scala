package eu.stratosphere.peel.extensions.hadoop.beans.system

import eu.stratosphere.peel.core.beans.system.{FileSystem, System}
import eu.stratosphere.peel.core.util.shell
import eu.stratosphere.peel.core.util.Version

private[system] trait HDFSFileSystem extends FileSystem {
  self: System =>

  override def exists(path: String) = {
    val hadoopHome = config.getString(s"system.$configKey.path.home")
    (shell !! s"""if $hadoopHome/bin/hadoop fs -test -e "$path" ; then echo "YES" ; else echo "NO"; fi""").trim() == "YES"
  }

  override def rmr(path: String, skipTrash: Boolean = true) = {
    val hadoopHome = config.getString(s"system.$configKey.path.home")
    // assemble options
    val opts = Seq(
      if (Version(version) < Version("2")) Some("-rmr") else Some("-rm -r"),
      if (skipTrash) Some("-skipTrash") else Option.empty[String]
    )
    // execute command
    shell !( s"""$hadoopHome/bin/hadoop fs ${opts.flatten.mkString(" ")} "$path" """, "Unable to remove path in HDFS.")
  }

  override def copyFromLocal(src: String, dst: String) = {
    val hadoopHome = config.getString(s"system.$configKey.path.home")
    if (src.endsWith(".gz"))
      shell !( s"""gunzip -c \"$src\" | $hadoopHome/bin/hadoop fs -put - \"$dst\" """, "Unable to copy file to HDFS.")
    else
      shell !( s"""$hadoopHome/bin/hadoop fs -copyFromLocal "$src" "$dst" """, "Unable to copy file to HDFS.")
  }

  def mkdir(dir: String) = {
    val hadoopHome = config.getString(s"system.$configKey.path.home")
    shell !( s"""$hadoopHome/bin/hadoop fs -mkdir -p "$dir" """, "Unable to create directory in HDFS.")
  }
}
