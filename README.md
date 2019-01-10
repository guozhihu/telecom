# 电信客服项目
## 项目背景
通信运营商每时每刻会产生大量的通信数据，例如通话记录，短信记录，彩信记录，第三方
服务资费等等繁多信息。数据量如此巨大，除了要满足用户的实时查询和展示之外，还需要
定时定期的对已有数据进行离线的分析处理。例如，当日话单，月度话单，季度话单，年度
话单，通话详情，通话记录等等。以此为背景，寻找一个切入点，学习其中的方法论。
 
## 系统环境
linux版本: CentOS Linux release 7.5.1804 (Core)   
jdk: Java(TM) SE Runtime Environment (build 1.8.0_144-b01)  
scala: scala-2.11.8  
flume: flume-ng-1.6.0-cdh5.7.0  
zookeeper: zookeeper-3.4.5-cdh5.7.0  
kafka: kafka_2.11-2.1.0  
hadoop: hadoop-2.6.0-cdh5.7.0  
hbase: hbase-1.2.0-cdh5.7.0  

## 项目实现
### 数据生产
* 作用<br>
随机产生通话记录，包括主叫，被叫，通话时间，通话时长，保存到文件中(我这里是保存到calllog文件中)  
* 操作步骤  
1.将ct_producer模块打成jar包，并发送到服务器上  
2.编写通话记录数据生成脚本productCalllog.sh，内容如下：<br>
\#!/bin/bash
java -cp /home/hadoop/data/telecom_project/jars/ct_producer-1.0-SNAPSHOT.jar producer.ProductLog /home/hadoop/data/telecom_project/callLog/calllog.csv  
3.执行脚本  
\> chmod 755 /home/hadoop/data/telecom_project/shell_scripts/productCalllog.sh  
\> /home/hadoop/data/telecom_project/shell_scripts/productCalllog.sh
### 数据采集
* 作用<br>
1. Flume采集实时写入到文件的数据(这里的文件指数据生产模块的calllog文件)
2. Kafka接收Flume实时递交进来的数据<br>
3. Hbase实时保存一条一条流入的数据<br>
* 操作步骤<br>
1.启动zookeeper集群和kafka集群<br>
2.创建kafka主题calllog<br>
kafka-topics.sh --zookeeper mini1:2181,mini2:2181,mini3:2181 --partitions 3 --replication-factor 3 --topic calllog --create<br>
3.启动kafka控制台消费者（此消费者只用于测试使用）<br>
kafka-console-consumer.sh --bootstrap-server mini1:9092,mini2:9092,mini3:9092 --topic calllog --from-beginning  0<br>
4.配置flume，监控calllog.csv日志文件<br>
编辑flume-calllog2kafka.conf配置文件，文件内容位于ct工程根目录下<br>
5.运行日志生产脚本，将数据导入kafka中<br>
编辑脚本<br>
\> vim flume_product_calllog_2_kafka.sh  
\#!/bin/bash  
/home/hadoop/app/apache-flume-1.6.0-cdh5.7.0-bin/bin/flume-ng agent --conf /home/hadoop/app/apache-flume-1.6.0-cdh5.7.0-bin/conf/ --name a1 --conf-file /home/hadoop/data/telecom_project/conf/flume/flume-calllog2kafka.conf  
执行脚本<br>
\> chmod 755 /home/hadoop/data/telecom_project/shell_scripts/flume_product_calllog_2_kafka.sh  
\> /home/hadoop/data/telecom_project/shell_scripts/flume_product_calllog_2_kafka.sh  
6.观察第3步骤的kafka消费者控制台，检测是否有数据导入到calllog主题中

### 数据分析