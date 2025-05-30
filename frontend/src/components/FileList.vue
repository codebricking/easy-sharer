<template>
  <div class="file-list-container">
    <!-- 面包屑导航 -->
    <el-breadcrumb class="breadcrumb" separator="/">
      <el-breadcrumb-item :to="{ path: '/', query: { path: '' } }">
        <el-icon><HomeFilled /></el-icon>
        首页
      </el-breadcrumb-item>
      <el-breadcrumb-item 
        v-for="(crumb, index) in breadcrumbs" 
        :key="index"
        :to="{ path: '/browse', query: { path: crumb.path } }">
        {{ crumb.name }}
      </el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 操作栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button @click="refreshFileList" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button v-if="uploadEnabled" type="primary" @click="selectFiles">
          <el-icon><Upload /></el-icon>
          选择文件上传
        </el-button>
      </div>
      <div class="toolbar-right">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文件..."
          style="width: 200px"
          clearable>
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>
    </div>

    <!-- 上传区域 -->
    <div v-if="uploadEnabled" 
         class="upload-area"
         :class="{ 'drag-over': isDragOver }"
         @drop="handleDrop"
         @dragover="handleDragOver"
         @dragleave="handleDragLeave">
      <div class="upload-content">
        <el-icon class="upload-icon"><Upload /></el-icon>
        <p>将文件拖拽到此处，或者 <el-button text type="primary" @click="selectFiles">点击选择文件</el-button></p>
        <p class="upload-tip">当前上传目录: {{ currentPathDisplay }}</p>
      </div>
    </div>

    <!-- 文件列表 -->
    <el-table 
      :data="filteredFiles" 
      :loading="loading"
      stripe
      style="width: 100%"
      @row-click="handleRowClick">
      <el-table-column width="50">
        <template #default="{ row }">
          <el-icon :class="getFileIconClass(row)">
            <component :is="getFileIcon(row)" />
          </el-icon>
        </template>
      </el-table-column>
      
      <el-table-column prop="name" label="文件名" min-width="300">
        <template #default="{ row }">
          <el-button 
            v-if="row.directory"
            text 
            type="primary" 
            @click="navigateToFolder(row.name)"
            class="file-name-btn">
            {{ row.name }}
          </el-button>
          <span v-else class="file-name">{{ row.name }}</span>
        </template>
      </el-table-column>
      
      <el-table-column prop="formattedSize" label="大小" width="120" />
      
      <el-table-column prop="formattedLastModified" label="修改时间" width="180" />
      
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-space>
            <el-button 
              v-if="!row.directory"
              type="primary" 
              size="small"
              @click="downloadFile(row)">
              <el-icon><Download /></el-icon>
              下载
            </el-button>
            <el-button 
              v-if="!row.directory"
              type="success" 
              size="small"
              @click="shareFile(row)">
              <el-icon><Share /></el-icon>
              分享
            </el-button>
          </el-space>
        </template>
      </el-table-column>
    </el-table>

    <!-- 空状态 -->
    <el-empty v-if="!loading && filteredFiles.length === 0" description="此目录为空" />

    <!-- 隐藏的文件输入框 -->
    <input 
      ref="fileInput" 
      type="file" 
      multiple 
      style="display: none" 
      @change="handleFileSelect" />

    <!-- 分享链接对话框 -->
    <el-dialog v-model="shareDialogVisible" title="分享链接" width="500px">
      <div class="share-dialog-content">
        <el-input 
          v-model="shareUrl" 
          readonly 
          class="share-url-input">
          <template #append>
            <el-button @click="copyShareUrl">
              <el-icon><CopyDocument /></el-icon>
              复制
            </el-button>
          </template>
        </el-input>
        <div class="share-qr-code" v-if="shareQrCode">
          <img :src="shareQrCode" alt="分享二维码" />
        </div>
      </div>
    </el-dialog>

    <!-- 上传进度对话框 -->
    <el-dialog v-model="uploadDialogVisible" title="文件上传" width="400px" :close-on-click-modal="false">
      <div class="upload-progress">
        <div v-for="(file, index) in uploadingFiles" :key="index" class="upload-file-item">
          <div class="upload-file-name">{{ file.name }}</div>
          <el-progress :percentage="file.progress" :status="file.status" />
        </div>
      </div>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

export default {
  name: 'FileList',
  props: {
    selectedIp: {
      type: String,
      default: ''
    },
    serverPort: {
      type: Number,
      default: 8080
    }
  },
  setup(props) {
    const route = useRoute()
    const router = useRouter()
    
    const loading = ref(false)
    const files = ref([])
    const uploadEnabled = ref(false)
    const currentPath = ref('')
    const searchKeyword = ref('')
    const isDragOver = ref(false)
    const shareDialogVisible = ref(false)
    const shareUrl = ref('')
    const shareQrCode = ref('')
    const uploadDialogVisible = ref(false)
    const uploadingFiles = ref([])
    const fileInput = ref(null)

    // 计算属性
    const filteredFiles = computed(() => {
      if (!searchKeyword.value) return files.value
      return files.value.filter(file => 
        file.name.toLowerCase().includes(searchKeyword.value.toLowerCase())
      )
    })

    const breadcrumbs = computed(() => {
      if (!currentPath.value) return []
      const parts = currentPath.value.split('/').filter(part => part)
      const crumbs = []
      let path = ''
      
      for (const part of parts) {
        path += (path ? '/' : '') + part
        crumbs.push({ name: part, path })
      }
      
      return crumbs
    })

    const currentPathDisplay = computed(() => {
      return currentPath.value || '根目录'
    })

    // 方法
    const loadFiles = async (path = '') => {
      loading.value = true
      try {
        const response = await axios.get('/api/files', {
          params: { path }
        })
        files.value = response.data.files || []
        currentPath.value = path
      } catch (error) {
        console.error('加载文件列表失败:', error)
        ElMessage.error('加载文件列表失败: ' + (error.response?.data?.message || error.message))
      } finally {
        loading.value = false
      }
    }

    const loadConfig = async () => {
      try {
        const response = await axios.get('/api/debug/config')
        uploadEnabled.value = response.data.uploadEnabled
      } catch (error) {
        console.error('加载配置失败:', error)
      }
    }

    const refreshFileList = () => {
      loadFiles(currentPath.value)
    }

    const navigateToFolder = (folderName) => {
      const newPath = currentPath.value ? `${currentPath.value}/${folderName}` : folderName
      router.push({ path: '/browse', query: { path: newPath } })
    }

    const handleRowClick = (row) => {
      if (row.directory) {
        navigateToFolder(row.name)
      }
    }

    const downloadFile = (file) => {
      const filePath = currentPath.value ? `${currentPath.value}/${file.name}` : file.name
      const url = `/download/${encodeURIComponent(filePath)}`
      window.open(url, '_blank')
    }

    const shareFile = async (file) => {
      try {
        const filePath = currentPath.value ? `${currentPath.value}/${file.name}` : file.name
        
        // 生成分享链接 - 使用选中的IP地址
        const selectedIpAddress = props.selectedIp || 'localhost'
        const port = props.serverPort || 8080
        const baseUrl = `http://${selectedIpAddress}:${port}`
        shareUrl.value = `${baseUrl}/download/${encodeURIComponent(filePath)}`
        
        shareDialogVisible.value = true
        
        // 生成二维码
        generateQrCode(shareUrl.value)
      } catch (error) {
        console.error('生成分享链接失败:', error)
        ElMessage.error('生成分享链接失败')
      }
    }

    const generateQrCode = (url) => {
      // 使用在线二维码生成服务
      shareQrCode.value = `https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=${encodeURIComponent(url)}`
    }

    const copyShareUrl = async () => {
      try {
        // 优先使用现代剪贴板API
        if (navigator.clipboard && navigator.clipboard.writeText) {
          await navigator.clipboard.writeText(shareUrl.value)
          ElMessage.success('分享链接已复制到剪贴板')
        } else {
          // 降级到传统方法
          copyToClipboardFallback(shareUrl.value)
        }
      } catch (error) {
        console.error('复制失败:', error)
        // 如果现代API失败，尝试降级方法
        try {
          copyToClipboardFallback(shareUrl.value)
        } catch (fallbackError) {
          console.error('降级复制也失败:', fallbackError)
          ElMessage.error('复制失败，请手动复制链接')
        }
      }
    }

    // 降级复制方法
    const copyToClipboardFallback = (text) => {
      const textArea = document.createElement('textarea')
      textArea.value = text
      textArea.style.position = 'fixed'
      textArea.style.left = '-999999px'
      textArea.style.top = '-999999px'
      document.body.appendChild(textArea)
      textArea.focus()
      textArea.select()
      
      try {
        const successful = document.execCommand('copy')
        if (successful) {
          ElMessage.success('分享链接已复制到剪贴板')
        } else {
          throw new Error('execCommand copy failed')
        }
      } finally {
        document.body.removeChild(textArea)
      }
    }

    const selectFiles = () => {
      fileInput.value?.click()
    }

    const handleFileSelect = (event) => {
      const files = Array.from(event.target.files)
      if (files.length > 0) {
        uploadFiles(files)
      }
    }

    const handleDrop = (event) => {
      event.preventDefault()
      isDragOver.value = false
      
      const files = Array.from(event.dataTransfer.files)
      if (files.length > 0) {
        uploadFiles(files)
      }
    }

    const handleDragOver = (event) => {
      event.preventDefault()
      isDragOver.value = true
    }

    const handleDragLeave = (event) => {
      event.preventDefault()
      isDragOver.value = false
    }

    const uploadFiles = async (files) => {
      if (!uploadEnabled.value) {
        ElMessage.error('上传功能未启用')
        return
      }

      uploadingFiles.value = files.map(file => ({
        name: file.name,
        progress: 0,
        status: 'active'
      }))
      uploadDialogVisible.value = true

      const formData = new FormData()
      files.forEach(file => {
        formData.append('files', file)
      })
      formData.append('path', currentPath.value)

      try {
        const response = await axios.post('/api/upload', formData, {
          headers: {
            'Content-Type': 'multipart/form-data'
          },
          onUploadProgress: (progressEvent) => {
            const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total)
            uploadingFiles.value.forEach(file => {
              file.progress = progress
            })
          }
        })

        uploadingFiles.value.forEach(file => {
          file.progress = 100
          file.status = 'success'
        })

        ElMessage.success(`成功上传 ${files.length} 个文件`)
        setTimeout(() => {
          uploadDialogVisible.value = false
          refreshFileList()
        }, 1000)

      } catch (error) {
        console.error('上传失败:', error)
        uploadingFiles.value.forEach(file => {
          file.status = 'exception'
        })
        ElMessage.error('上传失败: ' + (error.response?.data?.message || error.message))
      }
    }

    const getFileIcon = (file) => {
      if (file.directory) return 'FolderOpened'
      
      const ext = file.name.split('.').pop()?.toLowerCase()
      
      if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'svg'].includes(ext)) return 'Picture'
      if (['mp4', 'avi', 'mov', 'wmv', 'flv', 'mkv'].includes(ext)) return 'VideoPlay'
      if (['mp3', 'wav', 'flac', 'aac', 'ogg'].includes(ext)) return 'Headphone'
      if (['pdf'].includes(ext)) return 'Document'
      if (['doc', 'docx'].includes(ext)) return 'Document'
      if (['xls', 'xlsx'].includes(ext)) return 'Grid'
      if (['ppt', 'pptx'].includes(ext)) return 'Document'
      if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return 'Box'
      if (['txt', 'md', 'log'].includes(ext)) return 'Document'
      
      return 'Document'
    }

    const getFileIconClass = (file) => {
      if (file.directory) return 'folder-icon'
      return 'file-icon'
    }

    // 监听路由变化
    watch(() => route.query.path, (newPath) => {
      loadFiles(newPath || '')
    }, { immediate: true })

    onMounted(() => {
      loadConfig()
    })

    return {
      loading,
      files,
      filteredFiles,
      uploadEnabled,
      currentPath,
      currentPathDisplay,
      searchKeyword,
      breadcrumbs,
      isDragOver,
      shareDialogVisible,
      shareUrl,
      shareQrCode,
      uploadDialogVisible,
      uploadingFiles,
      fileInput,
      loadFiles,
      refreshFileList,
      navigateToFolder,
      handleRowClick,
      downloadFile,
      shareFile,
      copyShareUrl,
      selectFiles,
      handleFileSelect,
      handleDrop,
      handleDragOver,
      handleDragLeave,
      getFileIcon,
      getFileIconClass
    }
  }
}
</script>

<style scoped>
.file-list-container {
  max-width: 100%;
}

.breadcrumb {
  margin-bottom: 20px;
  padding: 10px;
  background-color: #f5f5f5;
  border-radius: 6px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 15px;
  background-color: white;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.toolbar-left {
  display: flex;
  gap: 10px;
}

.upload-area {
  margin-bottom: 20px;
  padding: 30px;
  border: 2px dashed #d9d9d9;
  border-radius: 6px;
  text-align: center;
  background-color: #fafafa;
  transition: all 0.3s ease;
}

.upload-area.drag-over {
  border-color: #409eff;
  background-color: #ecf5ff;
}

.upload-content {
  color: #666;
}

.upload-icon {
  font-size: 32px;
  color: #d9d9d9;
  margin-bottom: 10px;
}

.upload-tip {
  font-size: 12px;
  color: #999;
  margin-top: 5px;
}

.file-name-btn {
  padding: 0;
  height: auto;
  line-height: 1.5;
}

.file-name {
  color: #333;
}

.folder-icon {
  color: #409eff;
  font-size: 18px;
}

.file-icon {
  color: #666;
  font-size: 16px;
}

.share-dialog-content {
  text-align: center;
}

.share-url-input {
  margin-bottom: 20px;
}

.share-qr-code img {
  max-width: 150px;
  height: auto;
}

.upload-progress {
  max-height: 300px;
  overflow-y: auto;
}

.upload-file-item {
  margin-bottom: 15px;
}

.upload-file-name {
  margin-bottom: 5px;
  font-size: 14px;
  color: #333;
}
</style> 