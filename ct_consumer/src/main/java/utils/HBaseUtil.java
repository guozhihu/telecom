package utils;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * Author: zhihu
 * Description:
 * Date: Create in 17:10 2018/9/17
 */
public class HBaseUtil {
    
    /**
     * 判断表是否存在
     *
     * @param conf
     * @param tableName
     * @return
     * @throws IOException
     */
    public static boolean isExistTable(Configuration conf, String tableName) throws IOException {
        // 操作 HBase 表必须创建 Admin 对象
        Connection connection = ConnectionFactory.createConnection(conf);
        Admin admin = connection.getAdmin();
        boolean result = admin.tableExists(TableName.valueOf(tableName));
        admin.close();
        connection.close();
        return result;
    }
    
    
    /**
     * 初始化命名空间
     *
     * @param conf
     * @param namespace
     * @throws IOException
     */
    public static void initNamespace(Configuration conf, String namespace) throws IOException {
        Connection connection = ConnectionFactory.createConnection(conf);
        Admin admin = connection.getAdmin();
        NamespaceDescriptor nd = NamespaceDescriptor
            .create(namespace)
            .addConfiguration("CREATE_TIME", String.valueOf(System.currentTimeMillis()))
            .addConfiguration("AUTHOR", "zhihu")
            .build();
        
        admin.createNamespace(nd);
        
        admin.close();
    }
    
    /**
     * 创建表：协处理器
     *
     * @param conf
     * @param tableName
     * @param regions
     * @param columnFamily
     * @throws IOException
     */
    public static void createTable(Configuration conf, String tableName, int regions, String... columnFamily) throws IOException {
        Connection connection = ConnectionFactory.createConnection(conf);
        Admin admin = connection.getAdmin();
        // 存在该表的话，就不用再创建了
        if (isExistTable(conf, tableName)) return;
        
        // 表不存在，开始创建表
        HTableDescriptor htd = new HTableDescriptor(TableName.valueOf(tableName));
        for (String cf : columnFamily) {
            htd.addFamily(new HColumnDescriptor(cf));
        }
        // 添加协处理器
        htd.addCoprocessor("hbase.CalleeWriteObserver");
        // 创建带有预分区的表
        admin.createTable(htd, genSplitKeys(regions));
        admin.close();
        connection.close();
    }
    
    /**
     * 辅助：创建分区键
     *
     * @param regions 要分多少个区
     * @return
     */
    private static byte[][] genSplitKeys(int regions) {
        //定义一个存放分区键的数组
        String[] keys = new String[regions];
        //目前推算，region个数不会超过2位数，所以region分区键格式化为两位数字所代表的字符串
        DecimalFormat df = new DecimalFormat("00");
        for (int i = 0; i < regions; i++) {
            // "|"在ASCII码中的值是最大的
            keys[i] = df.format(i) + "|";
        }
        
        byte[][] splitKeys = new byte[regions][];
        //生成byte[][]类型的分区键的时候，一定要保证分区键是有序的
        TreeSet<byte[]> treeSet = new TreeSet<>(Bytes.BYTES_COMPARATOR);
        for (int i = 0; i < regions; i++) {
            treeSet.add(Bytes.toBytes(keys[i]));
        }
        
        Iterator<byte[]> splitKeysIterator = treeSet.iterator();
        int index = 0;
        while (splitKeysIterator.hasNext()) {
            byte[] b = splitKeysIterator.next();
            splitKeys[index++] = b;
        }
        return splitKeys;
    }
    
    /**
     * 生成rowkey
     * regionCode_call1_buildTime_call2_flag_duration
     *
     * @return
     */
    public static String genRowKey(String regionCode, String call1, String buildTime, String call2, String flag, String duration) {
        StringBuilder sb = new StringBuilder();
        sb.append(regionCode + "_")
            .append(call1 + "_")
            .append(buildTime + "_")
            .append(call2 + "_")
            .append(flag + "_")
            .append(duration);
        return sb.toString();
    }
    
    /**
     * 手机号：15837312345
     * 通话建立时间：2017-01-10 11:20:30 -> 20170110112030
     *
     * @param call1
     * @param buildTime
     * @param regions
     * @return
     */
    public static String genRegionCode(String call1, String buildTime, int regions) {
        int len = call1.length();
        //取出后4位号码
        String lastPhone = call1.substring(len - 4);
        //取出年月
        String ym = buildTime
            .replaceAll("-", "")
            .replaceAll(":", "")
            .replaceAll(" ", "")
            .substring(0, 6);
        //离散操作1
        Integer x = Integer.valueOf(lastPhone) ^ Integer.valueOf(ym);
        int a = 10;
        int b = 20;
        a = a ^ b;
        b = a ^ b;
        a = a ^ b;
        //离散操作2
        int y = x.hashCode();
        //生成分区号
        int regionCode = y % regions;
        //格式化分区号
        DecimalFormat df = new DecimalFormat("00");
        return df.format(regionCode);
    }
}
