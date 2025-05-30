package tech.brick.easysharer.util;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

@Slf4j
public class NetworkUtils {
    
    /**
     * 获取本机局域网IP地址
     */
    public static String getLocalIpAddress() {
        try {
            // 遍历所有网络接口
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                // 跳过回环接口和虚拟接口
                if (networkInterface.isLoopback() || networkInterface.isVirtual() || !networkInterface.isUp()) {
                    continue;
                }
                
                // 遍历网络接口的所有IP地址
                for (InetAddress inetAddress : Collections.list(networkInterface.getInetAddresses())) {
                    // 只处理IPv4地址，且不是回环地址
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getHostAddress().indexOf(':') == -1) {
                        String ip = inetAddress.getHostAddress();
                        // 优先选择局域网IP地址
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip;
                        }
                    }
                }
            }
            
            // 如果没有找到局域网IP，返回第一个非回环IPv4地址
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostAddress();
            
        } catch (Exception e) {
            log.warn("获取本机IP地址失败", e);
            return "localhost";
        }
    }
} 