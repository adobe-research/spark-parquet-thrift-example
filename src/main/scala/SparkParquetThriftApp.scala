///////////////////////////////////////////////////////////////////////////
//
// Copyright (c) 2014 Adobe Systems Incorporated. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
///////////////////////////////////////////////////////////////////////////

package com.adobe.spark_parquet_thrift

// Scala collections.
import scala.collection.mutable.ArrayBuffer

// Spark.
import org.apache.spark
import spark.{SparkConf,SparkContext}
import spark.rdd.RDD
import org.apache.spark.SparkContext._

// Map Reduce.
import org.apache.hadoop.{conf,fs,mapreduce}
import fs.{FileSystem,Path}
import mapreduce.Job
import conf.Configuration

// File.
import com.google.common.io.Files
import java.io.File

// Parquet and Thrift support.
import parquet.hadoop.{ParquetOutputFormat, ParquetInputFormat}
import parquet.hadoop.thrift.{
  ParquetThriftInputFormat,ParquetThriftOutputFormat,
  ThriftReadSupport,ThriftWriteSupport
}

object SparkParquetThriftApp {
  def main(args: Array[String]) {
    val mem = "30g"
    println("Initializing Spark context.")
    println("  Memory: " + mem)
    val sparkConf = new SparkConf()
      .setAppName("SparkParquetThrift")
      .setMaster("local[1]")
      .setSparkHome("/usr/lib/spark")
      .setJars(Seq())
      .set("spark.executor.memory", mem)
    val sc = new SparkContext(sparkConf)

    println("Creating sample Thrift data.")
    val sampleData = Range(1,10).toSeq.map{ v: Int =>
      new SampleThriftObject("a"+v,"b"+v,"c"+v)
    }
    println(sampleData.map("  - " + _).mkString("\n"))

    val job = new Job()
    val parquetStore = "hdfs://server_address.com:8020/sample_store"
    println("Writing sample data to Parquet.")
    println("  - ParquetStore: " + parquetStore)
    ParquetThriftOutputFormat.setThriftClass(job, classOf[SampleThriftObject])
    ParquetOutputFormat.setWriteSupportClass(job, classOf[SampleThriftObject])
    sc.parallelize(sampleData)
      .map(obj => (null, obj))
      .saveAsNewAPIHadoopFile(
        parquetStore,
        classOf[Void],
        classOf[SampleThriftObject],
        classOf[ParquetThriftOutputFormat[SampleThriftObject]],
        job.getConfiguration
      )

    println("Reading 'col_a' and 'col_b' from Parquet data store.")
    ParquetInputFormat.setReadSupportClass(
      job,
      classOf[ThriftReadSupport[SampleThriftObject]]
    )
    job.getConfiguration.set("parquet.thrift.column.filter", "col_a;col_b")
    val parquetData = sc.newAPIHadoopFile(
      parquetStore,
      classOf[ParquetThriftInputFormat[SampleThriftObject]],
      classOf[Void],
      classOf[SampleThriftObject],
      job.getConfiguration
    ).map{case (void,obj) => obj}
    println(parquetData.collect().map("  - " + _).mkString("\n"))
  }
}
