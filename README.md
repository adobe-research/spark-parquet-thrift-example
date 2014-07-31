# Spark, Parquet, and Thrift Example.
[Apache Spark][spark] is a research project for distributed computing
which interacts with HDFS and heavily utilizes in-memory caching.
Modern datasets contain hundreds or thousands of columns and are
too large to cache all the columns in Spark's memory,
so Spark has to resort to paging to disk.
The disk paging penalty can be lessened or removed if the
Spark application only interacts with a subset of the columns
in the overall database by using a columnar store database
such as [Parquet][parquet], which will only load the specified
columns of data into a Spark [RDD][rdd].

[Matt Massie's example][spark-parquet-avro] uses
Parquet with [Avro][avro] for data serialization
and filters loading based on an equality predicate,
but does not show how to load only a subset of columns.
**This project shows a complete [Scala][scala]/[sbt][sbt] project
using [Thrift][thrift] for data serialization and shows how to filter
columnar subsets.**

The following portions explains the critical portions of code
and explains how to setup and run on your own cluster.
This has been developed in CentOS 6.5 with
sbt 0.13.5, Spark 1.0.0, Hadoop 2.0.0-cdh4.7.0,
and parquet-thrift 1.5.0.

```
> cat /etc/centos-release
CentOS release 6.5 (Final)

> sbt --version
sbt launcher version 0.13.5

> thrift --version
Thrift version 0.9.1

> hadoop version
Hadoop 2.0.0-cdh4.7.0
```

# Code Walkthrough
## Sample Thrift Object.
The following is a simple Thrift schema for the objects we're
going to store in the Parquet database.
For a more detailed introduction to Thrift,
see [Thrift: The Missing Guide][thrift-guide].

```Thrift
namespace java com.adobe.spark_parquet_thrift

struct SampleThriftObject {
  10: string col_a;
  20: string col_b;
  30: string col_c;
}
```

## Spark Context Initialization.
The remaining code snippets are from the Scala application,
which will be run as a local Spark application.
Make sure to change `sparkHome` and set `mem` to the
maximum amount of memory available on your computer.

```Scala
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
```

**Output.**
```
Initializing Spark context.
  Memory: 30g
```

## Create Sample Thrift Data.
The following snippet creates 9 sample Thrift objects.

```Scala
println("Creating sample Thrift data.")
val sampleData = Range(1,10).toSeq.map{ v: Int =>
  new SampleThriftObject("a"+v,"b"+v,"c"+v)
}
println(sampleData.map("  - " + _).mkString("\n"))
```

**Output.**
```
Creating sample Thrift data.
  - SampleThriftObject(col_a:a1, col_b:b1, col_c:c1)
  - SampleThriftObject(col_a:a2, col_b:b2, col_c:c2)
  - SampleThriftObject(col_a:a3, col_b:b3, col_c:c3)
  - SampleThriftObject(col_a:a4, col_b:b4, col_c:c4)
  - SampleThriftObject(col_a:a5, col_b:b5, col_c:c5)
  - SampleThriftObject(col_a:a6, col_b:b6, col_c:c6)
  - SampleThriftObject(col_a:a7, col_b:b7, col_c:c7)
  - SampleThriftObject(col_a:a8, col_b:b8, col_c:c8)
  - SampleThriftObject(col_a:a9, col_b:b9, col_c:c9)
```

## Store to Parquet.
This portion creates an RDD from the sample objects and
serializes them to the Parquet store.

```Scala
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
```

**Output.**
```
ParquetStore: hdfs://server_address.com:8020/sample_store
Writing sample data to Parquet.
```

## Reading columns from Parquet.
This portion loads the columns specified in `parquet.thrift.column.filter`
from the Parquet store.
The glob syntax for the filter is defined in the
[Parquet Cascading documentation][parquet-cascading] as follows.
Columns not specified here are loaded as `null`.

+ **Exact match**: "name" will only fetch the name attribute.
+ **Alternative match**: "address/{street,zip}" will fetch both street and
  zip in the Address
+ **Wildcard match**: "\*" will fetch name and age,
  but not address, since address is a nested structure
+ **Recursive match**: "\*\*" will recursively match all attributes
  defined in Person.
+ **Joined match**: Multiple glob expression can be joined together separated
  by ";". eg. "name;address/street" will match only name and street in Address.

```Scala
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
```


**Output.**
```
Reading 'col_a' and 'col_b' from Parquet data store.
  - SampleThriftObject(col_a:a1, col_b:b1, col_c:null)
  - SampleThriftObject(col_a:a2, col_b:b2, col_c:null)
  - SampleThriftObject(col_a:a3, col_b:b3, col_c:null)
  - SampleThriftObject(col_a:a4, col_b:b4, col_c:null)
  - SampleThriftObject(col_a:a5, col_b:b5, col_c:null)
  - SampleThriftObject(col_a:a6, col_b:b6, col_c:null)
  - SampleThriftObject(col_a:a7, col_b:b7, col_c:null)
  - SampleThriftObject(col_a:a8, col_b:b8, col_c:null)
  - SampleThriftObject(col_a:a9, col_b:b9, col_c:null)
```

# Parquet store on HDFS.
Use the `hdfs` command for a simple peek into the Parquet store on hdfs.
The `_metadata` file follows the file format described in
[apache/incubator-parquet-format][parquet-format], and
the data is stored in part files ~20M in size.

```
> hdfs dfs -ls -h 'hdfs://server_address.com:8020/sample_store'
Found 3 items
-rw-r--r--   3 root hadoop          0 2014-07-28 12:46 hdfs://server_address.com:8020/sample_store/_SUCCESS
-rw-r--r--   3 root hadoop        781 2014-07-28 12:46 hdfs://server_address.com:8020/sample_store/_metadata
-rw-r--r--   3 root hadoop        946 2014-07-28 12:46 hdfs://server_address.com:8020/sample_store/part-r-00000.parquet
```

The columns are stored as JSON in the `_metadata` file along with other
binary information.

```
> hdfs dfs -cat 'hdfs://server_address.com:8020/sample_store/_metadata'
...
{
  "id" : "STRUCT",
  "children" : [ {
    "name" : "col_a",
    "fieldId" : 10,
    "requirement" : "DEFAULT",
    "type" : {
      "id" : "STRING"
    }
  }, {
    "name" : "col_b",
    "fieldId" : 20,
    "requirement" : "DEFAULT",
    "type" : {
      "id" : "STRING"
    }
  }, {
    "name" : "col_c",
    "fieldId" : 30,
    "requirement" : "DEFAULT",
    "type" : {
      "id" : "STRING"
    }
  } ]
}
...
```

And, peeking into the part file shows the data stored by columns.

```
> hdfs dfs -cat \
  'hdfs://server_address.com:8020/sample_store/part-r-00000.parquet' \
  | hexdump -C
00000000  50 41 52 31 15 00 15 78  15 78 2c 15 12 15 00 15  |PAR1...x.x,.....|
00000010  06 15 08 00 00 02 00 00  00 12 01 02 00 00 00 61  |...............a|
00000020  31 02 00 00 00 61 32 02  00 00 00 61 33 02 00 00  |1....a2....a3...|
00000030  00 61 34 02 00 00 00 61  35 02 00 00 00 61 36 02  |.a4....a5....a6.|
00000040  00 00 00 61 37 02 00 00  00 61 38 02 00 00 00 61  |...a7....a8....a|
00000050  39 15 00 15 78 15 78 2c  15 12 15 00 15 06 15 08  |9...x.x,........|
...
```

# Building and running.
Building this project requires [sbt][sbt] and [Thrift][thrift]
binaries present on your `PATH`.
sbt reads build settings and installs maven dependencies from `build.sbt`
and `project/plugins.sbt`, including:
+ [bigtoast/sbt-thrift][sbt-thrift] to compile the Thrift schema to Java.
+ [sbt/sbt-assembly][sbt-assembly] to create a fat JAR to submit to Spark.


Assuming HDFS is set up and Spark is installed, update the
`memory`, `sparkHome`, and `parquetStore` fields in the
example Scala application to your configuration.
Create the fat JAR to `target/scala-2.10/SparkParquetThrift.jar` with the
command `sbt assembly`.

```
> sbt assembly
[info] Packaging /Users/amos/spark-parquet-thrift/target/scala-2.10/SparkParquetThrift.jar ...
[info] Done packaging.
```

If this leads to compilation errors,
go into an sbt shell by typing `sbt` and use the `~compile` feature
to watch the source directory while you attempt to fix them.
If `build.sbt` or `plugins.sbt` are changed, use the `sbt` shell command
`reload` to reload the build settings and dependencies before
compiling again.

Assuming the fat JAR was correctly produced,
change the following to your Spark installation directory and use
the `spark-submit` program to load the libraries and run the example.

```
> sudo /usr/lib/spark/bin/spark-submit \
  --class com.adobe.spark_parquet_thrift.SparkParquetThriftApp \
  --deploy-mode client \
  target/scala-2.10/SparkParquetThrift.jar
```

# License.
Licensed under the Apache Software License 2.0.

[spark]: http://spark.apache.org/
[parquet]: http://parquet.io/
[thrift]: https://thrift.apache.org/
[thrift-guide]: http://diwakergupta.github.io/thrift-missing-guide/
[avro]: http://avro.apache.org/
[parquet-cascading]: https://github.com/Parquet/parquet-mr/blob/master/parquet_cascading.md
[parquet-format]: https://github.com/apache/incubator-parquet-format

[scala]: http://scala-lang.org
[sbt]: http://www.scala-sbt.org/
[spark-parquet-avro]: http://zenfractal.com/2013/08/21/a-powerful-big-data-trio/
[rdd]: http://spark.apache.org/docs/latest/api/scala/index.html#org.apache.spark.rdd.RDD
[sbt-thrift]: https://github.com/bigtoast/sbt-thrift
[sbt-assembly]: https://github.com/sbt/sbt-assembly
