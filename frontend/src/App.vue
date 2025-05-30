<template>
  <div id="app">
    <el-container class="app-container">
      <el-header class="app-header">
        <div class="header-content">
          <h1 class="app-title">
            <el-icon class="title-icon"><Share /></el-icon>
            Easy Sharer
          </h1>
          <div class="header-info">
            <!-- IP选择下拉框 -->
            <el-select 
              v-if="serverInfo.localIps && serverInfo.localIps.length > 1" 
              v-model="selectedIp" 
              placeholder="选择IP地址"
              style="width: 200px; margin-right: 10px;"
              @change="handleIpChange">
              <el-option 
                v-for="ip in serverInfo.localIps" 
                :key="ip" 
                :label="`${ip}:${serverInfo.port}`" 
                :value="ip">
                <span style="float: left">{{ ip }}:{{ serverInfo.port }}</span>
                <span v-if="ip === serverInfo.localIps[0]" style="float: right; color: #8cc5ff; font-size: 12px">推荐</span>
              </el-option>
            </el-select>
            
            <!-- 当前IP显示 -->
            <el-tag v-if="selectedIp" type="success">
              <el-icon><Position /></el-icon>
              {{ selectedIp }}:{{ serverInfo.port }}
            </el-tag>
            
            <el-tag v-if="uploadEnabled" type="info">
              <el-icon><Upload /></el-icon>
              上传功能已启用
            </el-tag>
          </div>
        </div>
      </el-header>
      
      <el-main class="app-main">
        <router-view :selected-ip="selectedIp" :server-port="serverInfo.port" />
      </el-main>
      
      <el-footer class="app-footer">
        <div class="footer-content">
          <p>Easy Sharer - 简单易用的局域网文件分享工具</p>
          <div v-if="serverInfo.localIps && serverInfo.localIps.length > 1" class="network-tips">
            <el-tooltip content="点击查看所有可用IP地址" placement="top">
              <el-button text @click="showNetworkDialog = true">
                <el-icon><InfoFilled /></el-icon>
                网络信息
              </el-button>
            </el-tooltip>
          </div>
        </div>
      </el-footer>
    </el-container>

    <!-- 网络信息对话框 -->
    <el-dialog v-model="showNetworkDialog" title="网络信息" width="500px">
      <div v-if="serverInfo.localIps">
        <h4>可用的局域网地址：</h4>
        <el-space direction="vertical" fill style="width: 100%">
          <el-card v-for="(ip, index) in serverInfo.localIps" :key="ip" shadow="hover">
            <div class="ip-card">
              <el-tag :type="index === 0 ? 'success' : 'info'" size="large">
                {{ ip }}:{{ serverInfo.port }}
              </el-tag>
              <span v-if="index === 0" class="recommended">（推荐）</span>
              <el-button 
                v-if="ip !== selectedIp" 
                size="small" 
                type="primary" 
                @click="selectIp(ip)"
                style="margin-left: 10px;">
                选择此IP
              </el-button>
              <span v-else style="margin-left: 10px; color: #67c23a;">当前使用</span>
            </div>
          </el-card>
        </el-space>
        <el-alert
          title="网络提示"
          type="info"
          show-icon
          :closable="false"
          style="margin-top: 15px;">
          <p>如果其他设备无法访问，请检查：</p>
          <ul>
            <li>确保设备在同一局域网内</li>
            <li>检查防火墙设置</li>
            <li>尝试使用不同的IP地址</li>
          </ul>
        </el-alert>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import axios from 'axios'

export default {
  name: 'App',
  setup() {
    const showNetworkDialog = ref(false)
    const uploadEnabled = ref(false)
    const serverInfo = ref({})
    const selectedIp = ref('')

    const loadServerInfo = async () => {
      try {
        const response = await axios.get('/api/debug/config')
        uploadEnabled.value = response.data.uploadEnabled
        
        // 尝试获取服务器启动信息
        const startupResponse = await axios.get('/api/server-info')
        if (startupResponse.data) {
          serverInfo.value = startupResponse.data
          // 默认选择第一个IP（推荐IP）
          if (serverInfo.value.localIps && serverInfo.value.localIps.length > 0) {
            selectedIp.value = serverInfo.value.localIps[0]
          }
        }
      } catch (error) {
        console.error('加载服务器信息失败:', error)
      }
    }

    const selectIp = (ip) => {
      selectedIp.value = ip
      showNetworkDialog.value = false
    }

    const handleIpChange = (ip) => {
      selectedIp.value = ip
    }

    onMounted(() => {
      loadServerInfo()
    })

    return {
      showNetworkDialog,
      uploadEnabled,
      serverInfo,
      selectedIp,
      selectIp,
      handleIpChange
    }
  }
}
</script>

<style scoped>
.app-container {
  min-height: 100vh;
}

.app-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-bottom: 1px solid #e4e7ed;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.app-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  display: flex;
  align-items: center;
  color: white;
}

.title-icon {
  margin-right: 8px;
  font-size: 28px;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.app-main {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  min-height: calc(100vh - 120px);
}

.app-footer {
  background-color: #f5f5f5;
  border-top: 1px solid #e4e7ed;
  padding: 10px 0;
}

.footer-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  font-size: 14px;
  color: #666;
}

.network-tips {
  display: flex;
  align-items: center;
}

.ip-card {
  display: flex;
  align-items: center;
  gap: 10px;
}

.recommended {
  color: #67c23a;
  font-weight: 500;
}
</style> 