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
kafka-console-consumer.sh --bootstrap-server mini1:9092,mini2:9092,mini3:9092 --topic calllog --from-beginning 0<br>
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
7.打包并运行Hbase消费者代码  
将工程ct_consumer工程下的pom.xml文件拷贝到一个临时目录下，如lib目录下  
执行如下命令下载工程ct_consumer所有依赖的jar包  
mvn -DoutputDirectory=./lib -DgroupId=com.china -DartifactId=ct_consumer -Dversion=1.0-SNAPSHOT dependency:copy-dependencies  
将下载下来的lib目录上传到/home/hadoop/data/telecom_project/ct_consumer_lib/lib目录下  
将ct_consumer工程模块打包ct_consumer-1.0-SNAPSHOT.jar，上传到
/home/hadoop/data/telecom_project/ct_consumer_lib目录下  
8.运行脚本将kafka中的数据处理后存放到Hbase中  
\> vim kafka2hbase.sh  
\#!/bin/bash  
java -Djava.ext.dirs=/home/hadoop/data/telecom_project/ct_consumer_lib/lib/ -cp /home/hadoop/data/telecom_project/ct_consumer_lib/ct_consumer-1.0-SNAPSHOT.jar kafka.HBaseConsumer  
执行脚本<br>
\> chmod 755 /home/hadoop/data/telecom_project/shell_scripts/kafka2hbase.sh  
\> sh /home/hadoop/data/telecom_project/shell_scripts/kafka2hbase.sh  
这里还有一种方式，但是不能用脚本执行该命令，只能手动执行该命令  
java -cp /home/hadoop/data/telecom_project/ct_consumer_lib/ct_consumer-1.0-SNAPSHOT.jar:/home/hado/data/telecom_project/ct_consumer_lib/lib/* kafka.HBaseConsumer  
9.优化数据存储方案  
(1)编写协处理器类CalleeWriteObserver，为表设置协处理器，即在“表描述器”中调用addCoprocessor方法进行协处理器的设置  
(2)在协处理器中，在一条主叫日志成功插入后，将该日志切换为被叫日志再次插入一次，放到与主叫日志不用的列族中  
(3)重新编译ct_consumer工程，将ct_consumer-1.0-SNAPSHOT.jar群发到所有HBase的物理机器上，放到$HBASE_HOME/lib目录下  
(4)在一台机器上修改hbase-site.xml  
\<property>  
&ensp;&ensp;&ensp;&ensp;&ensp;\<name>hbase.coprocessor.region.classes\</name>  
&ensp;&ensp;&ensp;&ensp;&ensp;\<value>com.china.coprocessor.CalleeWriteObserver\</value>  
\</property>  
修改后群发到其他HBase所在的机器节点上<br>
重新启动Hbase集群
### 数据分析
* 作用<br>
将采集到的数据(即数据采集模块中采集到Hbase中的数据)进行具体的业务分析，将分析结果保存到mysql中，以便web端实时查询分析结果。<br>
1)用户每天主叫通话个数统计，通话时间统计。<br>
2)用户每月通话记录统计，通话时间统计。<br>
3)用户每年通话记录统计，通话时间统计。<br>
* Mysql中的表结构设计<br>
1) db_telecom.tb_contacts <br>
用于存放用户手机号码与联系人姓名。<br>
|   列   |  备注   |  类型  |
| :----: | :----: | :----: |
|id        | 自增主键   | int(11) NOT NULL      | 
|telephone | 手机号码   | varchar(255) NOT NULL | 
|name      | 联系人姓名 | varchar(255) NOT NULL |
<br>
2) db_telecom.tb_call <br>
用于存放某个时间维度下通话次数与通话时长的总和。<br>
|   列   |  备注   |  类型  |
| :----: | :----: | :----: |
|id_date_contact  |自增主键        | varchar(255) NOT NULL    | 
|id_date_dimension|时间维度id      | int(11) NOT NULL         | 
|id_contact       |查询人的电话号码 | int(11) NOT NULL         |
|call_sum         |通话次数总和     |int(11) NOT NULL DEFAULT 0| 
|call_duration_sum|通话时长总和     |int(11) NOT NULL DEFAULT 0| 
<br>
3) db_telecom.tb_dimension_date 
用于存放时间维度的相关数据 
|   列   |  备注   |  类型  |
| :----: | :----: | :----: |
|id      | 自增主键                                               | int(11) NOT NULL | 
|year    | 年，当前通话信息所在年                                   | int(11) NOT NULL | 
|month   | 月，当前通话信息所在月，如果按照年来统计信息，则month 为-1。| int(11) NOT NULL |
|day     | 日，当前通话信息所在日，如果是按照月来统计信息，则day 为-1。| int(11) NOT NULL |
<br>
