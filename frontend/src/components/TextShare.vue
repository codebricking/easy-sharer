<template>
  <div class="text-share-container">
    <!-- 标题和统计信息 -->
    <div class="header-section">
      <h2 class="section-title">
        <el-icon><ChatDotRound /></el-icon>
        文本分享板块
      </h2>
      <div class="stats-info" v-if="stats">
        <el-space>
          <el-tag type="info">总分享: {{ stats.totalShares }}</el-tag>
          <el-tag type="success">总浏览: {{ stats.totalViews }}</el-tag>
          <el-tag type="warning">活跃IP: {{ stats.uniqueIps }}</el-tag>
          <el-tooltip content="文本分享数据存储位置" placement="bottom" v-if="stats.dataFilePath">
            <el-tag type="primary" style="cursor: help;">
              <el-icon><Folder /></el-icon>
              数据存储
            </el-tag>
          </el-tooltip>
        </el-space>
        <div v-if="stats.dataFilePath" class="storage-info">
          <el-text size="small" type="info">
            <el-icon><FolderOpened /></el-icon>
            存储路径: {{ stats.dataFilePath }}
          </el-text>
        </div>
      </div>
    </div>

    <!-- 创建新分享区域 -->
    <el-card class="create-section" shadow="hover">
      <template #header>
        <div class="card-header">
          <span><el-icon><Edit /></el-icon> 发布新的文本分享</span>
          <el-button type="primary" @click="showCreateDialog = true">
            <el-icon><Plus /></el-icon>
            新建分享
          </el-button>
        </div>
      </template>
      
      <div class="quick-create">
        <el-input
          v-model="quickContent"
          placeholder="快速分享一段文本..."
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          @keydown.ctrl.enter="quickCreateShare"
        />
        <div class="quick-actions">
          <el-space>
            <el-select v-model="quickType" placeholder="类型" style="width: 120px">
              <el-option label="问题" value="问题" />
              <el-option label="笔记" value="笔记" />
              <el-option label="代码" value="代码" />
              <el-option label="链接" value="链接" />
              <el-option label="其他" value="其他" />
            </el-select>
            <el-button 
              type="primary" 
              @click="quickCreateShare"
              :disabled="!quickContent.trim()"
              :loading="creating">
              <el-icon><Share /></el-icon>
              分享 (Ctrl+Enter)
            </el-button>
          </el-space>
        </div>
      </div>
    </el-card>

    <!-- 分享列表 -->
    <div class="shares-section">
      <div class="section-header">
        <h3>最新分享</h3>
        <el-space>
          <el-button @click="loadShares" :loading="loading">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
          <el-button @click="showMyShares = !showMyShares" :type="showMyShares ? 'primary' : ''">
            <el-icon><User /></el-icon>
            我的分享
          </el-button>
        </el-space>
      </div>

      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="3" animated />
      </div>

      <div v-else-if="displayShares.length === 0" class="empty-state">
        <el-empty description="暂无分享内容">
          <el-button type="primary" @click="showCreateDialog = true">创建第一个分享</el-button>
        </el-empty>
      </div>

      <div v-else class="shares-list">
        <el-card 
          v-for="share in displayShares" 
          :key="share.id" 
          class="share-item" 
          shadow="hover">
          <div class="share-header">
            <div class="share-meta">
              <el-avatar :size="32" class="user-avatar">
                {{ share.nickname ? share.nickname.charAt(0) : share.ipAddress.split('.').pop() }}
              </el-avatar>
              <div class="user-info">
                <div class="user-name">
                  {{ share.nickname || `用户${share.ipAddress.split('.').pop()}` }}
                  <el-tag v-if="isMyShare(share)" type="success" size="small">我的</el-tag>
                </div>
                <div class="share-time">
                  <el-icon><Clock /></el-icon>
                  {{ formatTime(share.shareTime) }}
                  <span class="ip-address">来自 {{ share.ipAddress }}</span>
                </div>
              </div>
            </div>
            <div class="share-actions">
              <el-tag v-if="share.type" :type="getTypeColor(share.type)" size="small">
                {{ share.type }}
              </el-tag>
              <el-button 
                @click="copyContent(share.content)" 
                type="primary" 
                size="small" 
                circle>
                <el-icon><CopyDocument /></el-icon>
              </el-button>
              <el-button 
                v-if="isMyShare(share)"
                @click="deleteShare(share.id)" 
                type="danger" 
                size="small" 
                circle>
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          
          <div class="share-content">
            <pre class="content-text">{{ share.content }}</pre>
          </div>
          
          <div class="share-footer">
            <el-space>
              <span class="view-count">
                <el-icon><View /></el-icon>
                {{ share.viewCount }} 次浏览
              </span>
              <span class="share-id">ID: {{ share.id }}</span>
            </el-space>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 创建分享对话框 -->
    <el-dialog 
      v-model="showCreateDialog" 
      title="创建新分享"
      width="600px"
      @close="resetCreateForm">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="昵称">
          <el-input 
            v-model="createForm.nickname" 
            placeholder="可选，不填写将使用IP显示"
            maxlength="20"
            show-word-limit />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="createForm.type" placeholder="选择分享类型">
            <el-option label="问题" value="问题" />
            <el-option label="笔记" value="笔记" />
            <el-option label="代码" value="代码" />
            <el-option label="链接" value="链接" />
            <el-option label="公告" value="公告" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input
            v-model="createForm.content"
            type="textarea"
            :rows="8"
            placeholder="输入要分享的文本内容..."
            maxlength="10000"
            show-word-limit />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showCreateDialog = false">取消</el-button>
          <el-button 
            type="primary" 
            @click="createShare"
            :disabled="!createForm.content.trim()"
            :loading="creating">
            创建分享
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted, computed } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

export default {
  name: 'TextShare',
  props: {
    selectedIp: String,
    serverPort: Number
  },
  setup(props) {
    const shares = ref([])
    const myShares = ref([])
    const stats = ref(null)
    const loading = ref(false)
    const creating = ref(false)
    const showCreateDialog = ref(false)
    const showMyShares = ref(false)
    
    // 快速创建
    const quickContent = ref('')
    const quickType = ref('其他')
    
    // 创建表单
    const createForm = ref({
      content: '',
      nickname: '',
      type: '其他'
    })

    // 显示的分享列表
    const displayShares = computed(() => {
      return showMyShares.value ? myShares.value : shares.value
    })

    // 加载所有分享
    const loadShares = async () => {
      loading.value = true
      try {
        const response = await axios.get('/api/text-shares')
        shares.value = response.data
      } catch (error) {
        console.error('加载分享失败:', error)
        ElMessage.error('加载分享失败')
      } finally {
        loading.value = false
      }
    }

    // 加载我的分享
    const loadMyShares = async () => {
      try {
        const response = await axios.get('/api/text-shares/my')
        myShares.value = response.data
      } catch (error) {
        console.error('加载我的分享失败:', error)
      }
    }

    // 加载统计信息
    const loadStats = async () => {
      try {
        const response = await axios.get('/api/text-shares/stats')
        stats.value = response.data
      } catch (error) {
        console.error('加载统计信息失败:', error)
      }
    }

    // 创建分享
    const createShare = async () => {
      if (!createForm.value.content.trim()) {
        ElMessage.warning('请输入分享内容')
        return
      }

      creating.value = true
      try {
        await axios.post('/api/text-share', createForm.value)
        ElMessage.success('分享创建成功')
        showCreateDialog.value = false
        resetCreateForm()
        await loadShares()
        await loadMyShares()
        await loadStats()
      } catch (error) {
        console.error('创建分享失败:', error)
        ElMessage.error(error.response?.data?.message || '创建分享失败')
      } finally {
        creating.value = false
      }
    }

    // 快速创建分享
    const quickCreateShare = async () => {
      if (!quickContent.value.trim()) {
        ElMessage.warning('请输入分享内容')
        return
      }

      creating.value = true
      try {
        await axios.post('/api/text-share', {
          content: quickContent.value,
          type: quickType.value,
          nickname: ''
        })
        ElMessage.success('分享创建成功')
        quickContent.value = ''
        await loadShares()
        await loadMyShares()
        await loadStats()
      } catch (error) {
        console.error('创建分享失败:', error)
        ElMessage.error(error.response?.data?.message || '创建分享失败')
      } finally {
        creating.value = false
      }
    }

    // 删除分享
    const deleteShare = async (shareId) => {
      try {
        await ElMessageBox.confirm('确定要删除这个分享吗？', '确认删除', {
          type: 'warning'
        })
        
        await axios.delete(`/api/text-share/${shareId}`)
        ElMessage.success('删除成功')
        await loadShares()
        await loadMyShares()
        await loadStats()
      } catch (error) {
        if (error !== 'cancel') {
          console.error('删除分享失败:', error)
          ElMessage.error(error.response?.data?.message || '删除失败')
        }
      }
    }

    // 复制内容
    const copyContent = async (content) => {
      try {
        await navigator.clipboard.writeText(content)
        ElMessage.success('内容已复制到剪贴板')
      } catch (error) {
        // 降级方案
        const textArea = document.createElement('textarea')
        textArea.value = content
        document.body.appendChild(textArea)
        textArea.select()
        document.execCommand('copy')
        document.body.removeChild(textArea)
        ElMessage.success('内容已复制到剪贴板')
      }
    }

    // 重置创建表单
    const resetCreateForm = () => {
      createForm.value = {
        content: '',
        nickname: '',
        type: '其他'
      }
    }

    // 判断是否是我的分享
    const isMyShare = (share) => {
      return myShares.value.some(myShare => myShare.id === share.id)
    }

    // 格式化时间
    const formatTime = (timeStr) => {
      const date = new Date(timeStr)
      const now = new Date()
      const diff = now - date
      
      if (diff < 60000) { // 1分钟内
        return '刚刚'
      } else if (diff < 3600000) { // 1小时内
        return `${Math.floor(diff / 60000)}分钟前`
      } else if (diff < 86400000) { // 1天内
        return `${Math.floor(diff / 3600000)}小时前`
      } else {
        return date.toLocaleDateString('zh-CN') + ' ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
      }
    }

    // 获取类型颜色
    const getTypeColor = (type) => {
      const colorMap = {
        '问题': 'warning',
        '笔记': 'info',
        '代码': 'success',
        '链接': 'primary',
        '公告': 'danger',
        '其他': ''
      }
      return colorMap[type] || ''
    }

    // 组件挂载时加载数据
    onMounted(() => {
      loadShares()
      loadMyShares()
      loadStats()
      
      // 定时刷新
      setInterval(() => {
        if (!showCreateDialog.value) {
          loadShares()
          loadStats()
        }
      }, 30000) // 30秒刷新一次
    })

    return {
      shares,
      myShares,
      stats,
      loading,
      creating,
      showCreateDialog,
      showMyShares,
      quickContent,
      quickType,
      createForm,
      displayShares,
      loadShares,
      createShare,
      quickCreateShare,
      deleteShare,
      copyContent,
      resetCreateForm,
      isMyShare,
      formatTime,
      getTypeColor
    }
  }
}
</script>

<style scoped>
.text-share-container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
}

.header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-title {
  margin: 0;
  font-size: 24px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.stats-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.storage-info {
  margin-top: 5px;
  padding: 5px 0;
  border-top: 1px solid #f0f0f0;
}

.storage-info .el-text {
  display: flex;
  align-items: center;
  gap: 4px;
  word-break: break-all;
}

.create-section {
  margin-bottom: 30px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.quick-create {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.quick-actions {
  display: flex;
  justify-content: flex-end;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.shares-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.share-item {
  transition: all 0.3s ease;
}

.share-item:hover {
  transform: translateY(-2px);
}

.share-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 15px;
}

.share-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-weight: bold;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.user-name {
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.share-time {
  font-size: 12px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 4px;
}

.ip-address {
  margin-left: 8px;
  color: #C0C4CC;
}

.share-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.share-content {
  margin: 15px 0;
  padding: 15px;
  background-color: #f8f9fa;
  border-radius: 6px;
  border-left: 3px solid #409eff;
}

.content-text {
  margin: 0;
  font-family: inherit;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  color: #303133;
}

.share-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
  font-size: 12px;
  color: #909399;
}

.view-count {
  display: flex;
  align-items: center;
  gap: 4px;
}

.share-id {
  font-family: monospace;
}

.loading-container {
  padding: 20px;
}

.empty-state {
  text-align: center;
  padding: 40px 20px;
}

@media (max-width: 768px) {
  .text-share-container {
    padding: 10px;
  }
  
  .header-section {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .share-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .share-actions {
    align-self: flex-end;
  }
}
</style> 