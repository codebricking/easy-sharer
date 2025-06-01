<template>
  <div id="app">
    <el-container class="app-container">
      <el-header class="app-header">
        <div class="header-content">
          <div class="header-left">
            <h1 class="app-title">
              <el-icon class="title-icon"><Share /></el-icon>
              Easy Sharer
            </h1>
            
            <!-- 功能切换按钮组 - 移到标题右边 -->
            <div class="function-switch-inline">
              <el-button-group class="switch-buttons">
                <el-button 
                  :type="currentTab === 'files' ? 'primary' : ''" 
                  @click="handleTabSelect('files')"
                  class="switch-btn">
                  <el-icon><Folder /></el-icon>
                  文件分享
                </el-button>
                <el-button 
                  :type="currentTab === 'text-share' ? 'primary' : ''" 
                  @click="handleTabSelect('text-share')"
                  class="switch-btn">
                  <el-icon><ChatDotRound /></el-icon>
                  文本分享
                </el-button>
              </el-button-group>
            </div>
          </div>
          
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
        <!-- 文件分享组件 -->
        <FileList 
          v-if="currentTab === 'files'"
          :selected-ip="selectedIp" 
          :server-port="serverInfo.port" />
          
        <!-- 文本分享组件 -->
        <TextShare 
          v-if="currentTab === 'text-share'"
          :selected-ip="selectedIp" 
          :server-port="serverInfo.port" />
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
import FileList from './components/FileList.vue'
import TextShare from './components/TextShare.vue'

export default {
  name: 'App',
  components: {
    FileList,
    TextShare
  },
  setup() {
    const showNetworkDialog = ref(false)
    const uploadEnabled = ref(false)
    const serverInfo = ref({})
    const selectedIp = ref('')
    const currentTab = ref('files') // 默认显示文件分享

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

    const handleTabSelect = (key) => {
      currentTab.value = key
    }

    onMounted(() => {
      loadServerInfo()
    })

    return {
      showNetworkDialog,
      uploadEnabled,
      serverInfo,
      selectedIp,
      currentTab,
      selectIp,
      handleIpChange,
      handleTabSelect
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
  padding: 0;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 70px;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  flex: 1;
}

.function-switch-inline {
  margin-left: 20px;
}

.switch-buttons {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  border-radius: 20px;
  overflow: hidden;
}

.switch-btn {
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  border: none;
  background: rgba(255, 255, 255, 0.9);
  color: #606266;
  transition: all 0.3s ease;
  min-width: 90px;
}

.switch-btn:hover {
  background: rgba(255, 255, 255, 1);
  color: #409eff;
  transform: translateY(-1px);
}

.switch-btn.el-button--primary {
  background: #409eff;
  color: white;
  box-shadow: 0 2px 4px rgba(64, 158, 255, 0.3);
}

.switch-btn.el-button--primary:hover {
  background: #337ecc;
  transform: translateY(-1px);
}

.app-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  display: flex;
  align-items: center;
  color: white;
  white-space: nowrap;
}

.title-icon {
  margin-right: 8px;
  font-size: 28px;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.app-main {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  min-height: calc(100vh - 140px);
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

/* 响应式设计 */
@media (max-width: 1024px) {
  .header-content {
    padding: 0 15px;
  }
  
  .function-switch-inline {
    margin-left: 15px;
  }
  
  .switch-btn {
    padding: 8px 12px;
    font-size: 12px;
    min-width: 80px;
  }
}

@media (max-width: 768px) {
  .header-content {
    flex-direction: column;
    height: auto;
    padding: 15px;
    gap: 15px;
  }
  
  .header-left {
    width: 100%;
    justify-content: space-between;
  }
  
  .function-switch-inline {
    margin-left: 0;
  }
  
  .header-info {
    width: 100%;
    justify-content: center;
    flex-wrap: wrap;
  }
  
  .app-title {
    font-size: 20px;
  }
  
  .title-icon {
    font-size: 24px;
  }
  
  .switch-btn {
    padding: 6px 10px;
    font-size: 11px;
    min-width: 70px;
  }
  
  .app-main {
    padding: 15px;
    min-height: calc(100vh - 160px);
  }
  
  .footer-content {
    flex-direction: column;
    gap: 10px;
    text-align: center;
  }
}

@media (max-width: 480px) {
  .header-left {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .function-switch-inline {
    align-self: center;
  }
  
  .switch-buttons {
    border-radius: 15px;
  }
  
  .switch-btn {
    padding: 5px 8px;
    font-size: 10px;
    min-width: 60px;
  }
}
</style> 