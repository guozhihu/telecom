package utils;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;

import java.io.IOException;

/**
 * Author: zhihu
 * Description:
 * Date: Create in 2019/1/12 13:33
 */
public class ConnectionInstanceUtil {
    
    private static Connection conn;
    public static synchronized Connection getConnection(Configuration conf) {
        try {
            if(conn == null || conn.isClosed()){
                conn = ConnectionFactory.createConnection(conf);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return conn;
    }
}
