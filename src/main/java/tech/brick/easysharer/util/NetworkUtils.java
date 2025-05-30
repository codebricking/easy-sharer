package tech.brick.easysharer.util;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class NetworkUtils {
    
    /**
     * 获取本机局域网IP地址（优先级最高的）
     */
    public static String getLocalIpAddress() {
        List<String> allIps = getAllLocalIpAddresses();
        return allIps.isEmpty() ? "localhost" : allIps.get(0);
    }
    
    /**
     * 获取所有本机局域网IP地址，按优先级排序
     * 优先级：192.168.x.x > 10.x.x.x > 172.16-31.x.x
     */
    public static List<String> getAllLocalIpAddresses() {
        List<IpInfo> ipInfos = new ArrayList<>();
        
        try {
            // 遍历所有网络接口
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                // 跳过回环接口、虚拟接口和未启用的接口
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                
                String interfaceName = networkInterface.getDisplayName();
                boolean isVirtual = isVirtualInterface(interfaceName);
                
                // 遍历网络接口的所有IP地址
                for (InetAddress inetAddress : Collections.list(networkInterface.getInetAddresses())) {
                    // 只处理IPv4地址，且不是回环地址
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getHostAddress().indexOf(':') == -1) {
                        String ip = inetAddress.getHostAddress();
                        
                        // 只选择局域网IP地址
                        if (isPrivateIp(ip)) {
                            int priority = getIpPriority(ip, isVirtual);
                            ipInfos.add(new IpInfo(ip, interfaceName, priority, isVirtual));
                            log.debug("发现局域网IP: {} (接口: {}, 优先级: {}, 虚拟: {})", 
                                    ip, interfaceName, priority, isVirtual);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("获取网络接口失败", e);
        }
        
        // 按优先级排序并返回IP列表
        return ipInfos.stream()
                .sorted(Comparator.comparingInt(IpInfo::getPriority))
                .map(IpInfo::getIp)
                .collect(Collectors.toList());
    }
    
    /**
     * 判断是否为私有IP地址
     */
    private static boolean isPrivateIp(String ip) {
        return ip.startsWith("192.168.") || 
               ip.startsWith("10.") || 
               (ip.startsWith("172.") && isIn172Range(ip));
    }
    
    /**
     * 检查是否在172.16.0.0/12范围内
     */
    private static boolean isIn172Range(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length == 4 && parts[0].equals("172")) {
                int secondOctet = Integer.parseInt(parts[1]);
                return secondOctet >= 16 && secondOctet <= 31;
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return false;
    }
    
    /**
     * 获取IP地址的优先级（数字越小优先级越高）
     */
    private static int getIpPriority(String ip, boolean isVirtual) {
        int basePriority;
        
        if (ip.startsWith("192.168.")) {
            basePriority = 1; // 最高优先级：家庭/办公网络
        } else if (ip.startsWith("10.")) {
            basePriority = 2; // 中等优先级：企业网络
        } else if (ip.startsWith("172.")) {
            basePriority = 3; // 较低优先级：企业网络
        } else {
            basePriority = 9; // 最低优先级
        }
        
        // 虚拟接口降低优先级
        if (isVirtual) {
            basePriority += 10;
        }
        
        return basePriority;
    }
    
    /**
     * 判断是否为虚拟网络接口
     */
    private static boolean isVirtualInterface(String interfaceName) {
        if (interfaceName == null) return false;
        
        String name = interfaceName.toLowerCase();
        return name.contains("vmware") || 
               name.contains("virtualbox") || 
               name.contains("virtual") ||
               name.contains("vbox") ||
               name.contains("hyper-v") ||
               name.contains("docker") ||
               name.contains("vethernet");
    }
    
    /**
     * IP信息类
     */
    private static class IpInfo {
        private final String ip;
        private final String interfaceName;
        private final int priority;
        private final boolean virtual;
        
        public IpInfo(String ip, String interfaceName, int priority, boolean virtual) {
            this.ip = ip;
            this.interfaceName = interfaceName;
            this.priority = priority;
            this.virtual = virtual;
        }
        
        public String getIp() { return ip; }
        public String getInterfaceName() { return interfaceName; }
        public int getPriority() { return priority; }
        public boolean isVirtual() { return virtual; }
    }
} 