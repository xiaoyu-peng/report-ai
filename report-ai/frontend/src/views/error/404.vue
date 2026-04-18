<template>
  <div class="error-page">
    <div class="error-content">
      <h1>404</h1>
      <h2>页面不存在</h2>
      <p>抱歉，您访问的页面不存在或已被移除</p>
      <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
    </div>
  </div>
</template>

<style scoped>
.error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.error-content {
  text-align: center;
  color: #fff;
}

.error-content h1 {
  font-size: 120px;
  font-weight: 700;
  margin: 0;
  line-height: 1;
}

.error-content h2 {
  font-size: 32px;
  font-weight: 600;
  margin: 20px 0;
}

.error-content p {
  font-size: 16px;
  margin-bottom: 30px;
  opacity: 0.8;
}
</style>
