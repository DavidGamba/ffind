import org.scalatest._
import com.gambaeng.utils
import com.gambaeng.utils.FileUtils
import scala.collection.mutable.ListBuffer

import java.io.File

class FileUtilsTest extends FlatSpec with Matchers {
  "FileUtils" should "getFileTree" in {
    // Location of the test directory structure
    val test_tree = new File("test_tree")
    val fileTree = FileUtils.getFileTree(test_tree)
    // Use the lines below to update this test
    // fileTree.foreach{ filename =>
    //   println( "new File(\"" + filename + "\")" + "," )
    // }
    fileTree should equal ( Stream(
      new File("test_tree"),
      new File("test_tree/.a"),
      new File("test_tree/.a/B"),
      new File("test_tree/.a/B/c"),
      new File("test_tree/.a/B/c/D"),
      new File("test_tree/.a/B/c/D/e"),
      new File("test_tree/.A"),
      new File("test_tree/.A/b"),
      new File("test_tree/.A/b/C"),
      new File("test_tree/.A/b/C/d"),
      new File("test_tree/.A/b/C/d/E"),
      new File("test_tree/A"),
      new File("test_tree/A/b"),
      new File("test_tree/A/b/C"),
      new File("test_tree/A/b/C/d"),
      new File("test_tree/A/b/C/d/E"),
      new File("test_tree/a"),
      new File("test_tree/a/B"),
      new File("test_tree/a/B/c"),
      new File("test_tree/a/B/c/D"),
      new File("test_tree/a/B/c/D/e")
      )
    )
  }

  it should "match_files" in {
    // Location of the test directory structure
    val test_tree = new File("test_tree")
    val nameFilter = new util.matching.Regex(
          s"""(?i)^(.*?)(d)(.*)$$""", "pre", "matched", "post")
    val files = FileUtils.getFileTree(test_tree)
    var results = ListBuffer[File]()
    FileUtils.match_files(test_tree, nameFilter)( (filename, m) =>
      m match {
        case Some(m) => {
          results += filename
        }
        case None => {}
      }
    )
    results should equal ( List(
      new File("test_tree/.a/B/c/D"),
      new File("test_tree/.A/b/C/d"),
      new File("test_tree/A/b/C/d"),
      new File("test_tree/a/B/c/D")
    ))
  }

  it should "check if file isBinary" in {
    implicit def file2RichFile(file: File) = new FileUtils.RichFile(file)
    val test_tree = new File("test_tree")
    var results = ListBuffer[File]()
    FileUtils.getFileTree(test_tree).foreach{ filename =>
      if(filename.isBinary)
        results += filename
    }
    results should equal ( List())
  }
}
