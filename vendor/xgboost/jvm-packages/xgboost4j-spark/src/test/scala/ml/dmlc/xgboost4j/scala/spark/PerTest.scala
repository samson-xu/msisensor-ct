/*
 Copyright (c) 2014 by Contributors

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package ml.dmlc.xgboost4j.scala.spark

import java.io.File

import ml.dmlc.xgboost4j.{LabeledPoint => XGBLabeledPoint}

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql._
import org.scalatest.{BeforeAndAfterEach, FunSuite}

trait PerTest extends BeforeAndAfterEach { self: FunSuite =>

  protected val numWorkers: Int = Runtime.getRuntime.availableProcessors()

  @transient private var currentSession: SparkSession = _

  def ss: SparkSession = getOrCreateSession
  implicit def sc: SparkContext = ss.sparkContext

  protected def sparkSessionBuilder: SparkSession.Builder = SparkSession.builder()
      .master("local[*]")
      .appName("XGBoostSuite")
      .config("spark.ui.enabled", false)
      .config("spark.driver.memory", "512m")
      .config("spark.task.cpus", 1)

  override def beforeEach(): Unit = getOrCreateSession

  override def afterEach() {
    synchronized {
      if (currentSession != null) {
        currentSession.stop()
        cleanExternalCache(currentSession.sparkContext.appName)
        currentSession = null
      }
    }
  }

  private def getOrCreateSession = synchronized {
    if (currentSession == null) {
      currentSession = sparkSessionBuilder.getOrCreate()
      currentSession.sparkContext.setLogLevel("ERROR")
    }
    currentSession
  }

  private def cleanExternalCache(prefix: String): Unit = {
    val dir = new File(".")
    for (file <- dir.listFiles() if file.getName.startsWith(prefix)) {
      file.delete()
    }
  }

  protected def buildDataFrame(
      labeledPoints: Seq[XGBLabeledPoint],
      numPartitions: Int = numWorkers): DataFrame = {
    import DataUtils._
    val it = labeledPoints.iterator.zipWithIndex
      .map { case (labeledPoint: XGBLabeledPoint, id: Int) =>
        (id, labeledPoint.label, labeledPoint.features)
      }

    ss.createDataFrame(sc.parallelize(it.toList, numPartitions))
      .toDF("id", "label", "features")
  }

  protected def buildDataFrameWithGroup(
      labeledPoints: Seq[XGBLabeledPoint],
      numPartitions: Int = numWorkers): DataFrame = {
    import DataUtils._
    val it = labeledPoints.iterator.zipWithIndex
      .map { case (labeledPoint: XGBLabeledPoint, id: Int) =>
        (id, labeledPoint.label, labeledPoint.features, labeledPoint.group)
      }

    ss.createDataFrame(sc.parallelize(it.toList, numPartitions))
      .toDF("id", "label", "features", "group")
  }
}