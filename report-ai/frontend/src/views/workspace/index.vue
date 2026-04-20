<template>
  <div class="workspace-container">
    <!-- Left: Config Panel -->
    <div class="config-panel">
      <el-card class="config-card">
        <template #header>
          <span class="card-title">
            <el-icon><EditPen /></el-icon>
            报告设置
          </span>
        </template>

        <el-form :model="form" label-position="top">
          <el-form-item label="报告标题">
            <el-input
              v-model="form.title"
              placeholder="输入报告标题"
              maxlength="100"
              show-word-limit
              :disabled="generating"
            />
          </el-form-item>

          <el-form-item label="核心主题">
            <el-input
              v-model="form.topic"
              type="textarea"
              :rows="3"
              placeholder="描述报告的核心主题和目标..."
              :disabled="generating"
            />
          </el-form-item>

          <el-form-item label="重点内容">
            <el-input
              v-model="form.keyPoints"
              type="textarea"
              :rows="4"
              placeholder="列出需要涵盖的重点内容，每行一条..."
              :disabled="generating"
            />
          </el-form-item>

          <el-form-item label="选择知识库">
            <el-select
              v-model="form.kbId"
              placeholder="选择参考知识库（可选）"
              clearable
              style="width: 100%"
              :disabled="generating"
            >
              <el-option
                v-for="kb in knowledgeBases"
                :key="kb.id"
                :label="kb.name"
                :value="kb.id"
              >
                <span>{{ kb.name }}</span>
                <span style="float: right; color: #999; font-size: 12px">
                  {{ kb.docCount ?? 0 }} 篇文档
                </span>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item v-if="form.kbId" label="检索条件">
            <div class="search-conditions">
              <div class="condition-row">
                <span class="condition-label">补充关键词</span>
                <div class="condition-tags">
                  <el-tag
                    v-for="(kw, i) in includeKeywords"
                    :key="'inc-' + i"
                    size="small"
                    closable
                    @close="includeKeywords.splice(i, 1)"
                  >{{ kw }}</el-tag>
                  <el-input
                    v-if="showIncludeInput"
                    ref="includeInputRef"
                    v-model="includeInputVal"
                    size="small"
                    style="width: 100px"
                    @keyup.enter="addIncludeKeyword"
                    @blur="addIncludeKeyword"
                  />
                  <el-button v-else size="small" @click="showIncludeInput = true">+ 添加</el-button>
                </div>
              </div>
              <div class="condition-row">
                <span class="condition-label">排除关键词</span>
                <div class="condition-tags">
                  <el-tag
                    v-for="(kw, i) in excludeKeywords"
                    :key="'exc-' + i"
                    size="small"
                    type="danger"
                    closable
                    @close="excludeKeywords.splice(i, 1)"
                  >{{ kw }}</el-tag>
                  <el-input
                    v-if="showExcludeInput"
                    ref="excludeInputRef"
                    v-model="excludeInputVal"
                    size="small"
                    style="width: 100px"
                    @keyup.enter="addExcludeKeyword"
                    @blur="addExcludeKeyword"
                  />
                  <el-button v-else size="small" type="danger" plain @click="showExcludeInput = true">+ 排除</el-button>
                </div>
              </div>
            </div>
          </el-form-item>

          <el-form-item label="外部数据">
            <div class="external-data-btns">
              <el-button
                size="small"
                type="success"
                plain
                :disabled="generating"
                @click="showMcpDialog = true"
              >
                <el-icon><Connection /></el-icon>
                晴天舆情
              </el-button>
              <el-button
                size="small"
                type="primary"
                plain
                :disabled="generating"
                @click="showWebSearchDialog = true"
              >
                <el-icon><Search /></el-icon>
                Web 搜索
              </el-button>
              <el-button
                size="small"
                type="warning"
                plain
                :disabled="generating"
                @click="showFetchDialog = true"
              >
                <el-icon><Link /></el-icon>
                URL 抓取
              </el-button>
            </div>
            <div v-if="mcpArticles.length > 0 || webSearchResults.length > 0" class="mcp-imported-hint">
              已引入 {{ mcpArticles.length }} 篇舆情 + {{ webSearchResults.length }} 条 Web 数据
            </div>
          </el-form-item>

          <el-form-item label="写作风格模板">
            <el-select
              v-model="form.templateId"
              placeholder="选择模板（可选）"
              clearable
              style="width: 100%"
              :disabled="generating"
            >
              <el-option
                v-for="t in templates"
                :key="t.id"
                :label="t.name"
                :value="t.id"
              >
                <div class="template-option">
                  <span class="template-option-name">{{ t.name }}</span>
                  <span class="template-option-desc">{{ t.description || '' }}</span>
                </div>
              </el-option>
            </el-select>
            <div v-if="selectedTemplateDesc" class="template-hint">
              <el-icon><InfoFilled /></el-icon>
              {{ selectedTemplateDesc }}
            </div>
          </el-form-item>

          <el-form-item label="生成深度">
            <el-radio-group
              v-model="form.generationDepth"
              :disabled="generating"
              class="depth-group"
            >
              <el-radio-button value="brief">
                简洁
                <span class="depth-hint-inline">~800 字</span>
              </el-radio-button>
              <el-radio-button value="standard">
                标准
                <span class="depth-hint-inline">~2000 字</span>
              </el-radio-button>
              <el-radio-button value="deep">
                深度
                <span class="depth-hint-inline">~4000 字</span>
              </el-radio-button>
            </el-radio-group>
            <div class="depth-footnote">影响 RAG top-k 检索数量与正文篇幅</div>
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            style="width: 100%"
            :loading="generating"
            :disabled="!canGenerate"
            @click="handleGenerate"
          >
            <el-icon v-if="!generating"><MagicStick /></el-icon>
            {{ generating ? 'AI 生成中...' : '开始生成报告' }}
          </el-button>
        </el-form>
      </el-card>
    </div>

    <!-- Right: Editor Panel -->
    <div class="editor-panel">
      <el-card class="editor-card">
        <template #header>
          <div class="editor-header">
            <span class="card-title">
              <el-icon><Document /></el-icon>
              报告内容
            </span>
            <div class="editor-actions">
              <el-tag v-if="wordCount > 0" type="info" effect="light">
                {{ wordCount }} 字
              </el-tag>
              <el-radio-group
                v-if="content"
                v-model="viewMode"
                size="small"
                :disabled="generating || rewriting"
              >
                <el-radio-button value="edit">编辑</el-radio-button>
                <el-radio-button value="preview">预览</el-radio-button>
              </el-radio-group>
              <el-button
                v-if="currentReportId && content"
                size="small"
                :loading="saving"
                :disabled="generating || rewriting"
                @click="saveReport"
              >
                <el-icon><Select /></el-icon>
                保存
              </el-button>
              <el-button
                v-if="content"
                size="small"
                type="success"
                plain
                :disabled="generating"
                @click="copyContent"
              >
                <el-icon><CopyDocument /></el-icon>
                复制
              </el-button>
              <el-dropdown
                v-if="currentReportId && content"
                trigger="click"
                :disabled="generating || rewriting"
                @command="handleExport"
              >
                <el-button size="small" type="primary" plain :loading="exporting">
                  <el-icon><Download /></el-icon>
                  导出
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="docx">导出 Word（.docx）</el-dropdown-item>
                    <el-dropdown-item command="pdf">导出 PDF（含角标）</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-dropdown
                v-if="currentReportId && content"
                trigger="click"
                :disabled="generating || rewriting"
                @command="handleRewrite"
              >
                <el-button size="small" type="warning" plain :loading="rewriting">
                  <el-icon><MagicStick /></el-icon>
                  改写
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="DATA_UPDATE">数据更新（替换时间/数据）</el-dropdown-item>
                    <el-dropdown-item command="ANGLE_SHIFT">视角调整（换观点/受众）</el-dropdown-item>
                    <el-dropdown-item command="EXPAND">内容扩展（补案例/章节）</el-dropdown-item>
                    <el-dropdown-item command="STYLE_SHIFT">风格转换（正式↔通俗）</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button
                v-if="currentReportId && content"
                size="small"
                type="success"
                plain
                :disabled="generating || rewriting"
                @click="handleRewrite('CONTINUATION')"
              >
                <el-icon><Promotion /></el-icon>
                续写
              </el-button>
            </div>
          </div>
        </template>

        <!-- Empty state -->
        <div v-if="!content && !generating" class="empty-state">
          <el-empty description="填写左侧配置后点击「开始生成报告」">
            <template #image>
              <el-icon style="font-size: 80px; color: #c0c4cc">
                <Document />
              </el-icon>
            </template>
          </el-empty>
        </div>

        <!-- Loading before first token -->
        <div v-if="generating && !content" class="generating-hint">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>AI 正在思考和撰写，请稍候...</span>
        </div>

        <!-- AI 生成进度条 -->
        <div v-if="generating && progressStep" class="progress-bar">
          <div class="progress-steps">
            <div
              v-for="(label, i) in progressSteps"
              :key="i"
              class="progress-step"
              :class="{
                active: i + 1 === progressStep.stepIndex,
                done: i + 1 < progressStep.stepIndex
              }"
            >
              <div class="step-dot">
                <el-icon v-if="i + 1 < progressStep.stepIndex"><Check /></el-icon>
                <el-icon v-else-if="i + 1 === progressStep.stepIndex" class="is-loading"><Loading /></el-icon>
                <span v-else>{{ i + 1 }}</span>
              </div>
              <span class="step-label">{{ label }}</span>
            </div>
          </div>
        </div>

        <!-- Streaming: Markdown 渐进渲染 + 打字光标 -->
        <div
          v-if="content && (generating || rewriting) && viewMode === 'edit'"
          ref="streamingEl"
          class="streaming-editor"
          @click="handleCiteClick"
          v-html="streamingHtml"
        />

        <!-- Editable textarea（非流式时手动编辑） -->
        <div v-if="content && !generating && !rewriting && viewMode === 'edit'" class="editor-wrapper">
          <el-input
            v-model="content"
            type="textarea"
            :rows="28"
            resize="none"
            class="report-editor"
            placeholder="报告内容将在此处流式显示..."
            @keydown="handleEditorKeydown"
          />
          <div class="ai-assist-bar">
            <div class="ai-assist-hint">
              <el-icon><MagicStick /></el-icon>
              <span>按 <kbd>Ctrl</kbd>+<kbd>Enter</kbd> 启用 AI 助手</span>
            </div>
            <div class="ai-assist-actions">
              <el-button size="small" type="primary" plain @click="showAiChat = true">
                <el-icon><ChatDotRound /></el-icon>
                AI 对话
              </el-button>
              <el-button size="small" plain @click="handleTranslate">
                <el-icon><Switch /></el-icon>
                中英翻译
              </el-button>
              <el-button size="small" plain @click="handleAutoFormat">
                <el-icon><SetUp /></el-icon>
                一键排版
              </el-button>
            </div>
          </div>
        </div>

        <!-- Markdown 预览（含 [n] 角标，点击跳转溯源面板，段落级改写） -->
        <div
          v-else-if="content && viewMode === 'preview'"
          ref="previewEl"
          class="report-preview section-editable"
          @click="handlePreviewClick"
          @mouseover="handleSectionHover"
          @mouseout="handlePreviewMouseout"
          v-html="renderedSectionHtml"
        />
      </el-card>
    </div>

    <!-- AI 对话浮窗 -->
    <el-dialog
      v-model="showAiChat"
      title="AI 写作助手"
      width="520px"
      top="10vh"
      :close-on-click-modal="false"
      class="ai-chat-dialog"
    >
      <div class="ai-chat-body">
        <div class="ai-chat-messages" ref="aiChatMessagesEl">
          <div v-if="aiChatMessages.length === 0" class="ai-chat-welcome">
            <el-icon :size="32" color="#6366f1"><MagicStick /></el-icon>
            <p>我是你的AI写作助手，可以帮你：</p>
            <div class="ai-chat-suggestions">
              <el-tag
                v-for="s in aiSuggestions"
                :key="s"
                effect="plain"
                size="small"
                class="ai-suggestion-tag"
                @click="aiChatInput = s"
              >
                {{ s }}
              </el-tag>
            </div>
          </div>
          <div
            v-for="(msg, i) in aiChatMessages"
            :key="i"
            class="ai-chat-msg"
            :class="msg.role"
          >
            <div class="msg-avatar">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
            <div class="msg-content">{{ msg.content }}</div>
            <el-button
              v-if="msg.role === 'assistant'"
              size="small"
              type="primary"
              link
              @click="insertAiResult(msg.content)"
            >
              插入到报告
            </el-button>
          </div>
        </div>
        <div class="ai-chat-input-bar">
          <el-input
            v-model="aiChatInput"
            placeholder="输入你的问题或指令..."
            @keyup.enter="handleAiChat()"
            :disabled="aiChatLoading"
          >
            <template #append>
              <el-button @click="handleAiChat()" :loading="aiChatLoading">发送</el-button>
            </template>
          </el-input>
        </div>
      </div>
    </el-dialog>

    <!-- MCP 数据引入弹窗 -->
    <el-dialog v-model="showMcpDialog" title="引入晴天 MCP 舆情数据" width="700px" top="6vh">
      <div class="mcp-dialog-body">
        <el-input
          v-model="mcpKeyword"
          placeholder="输入关键词搜索舆情文章..."
          :loading="mcpSearching"
          @keyup.enter="searchMcpArticles"
        >
          <template #append>
            <el-button @click="searchMcpArticles" :loading="mcpSearching">搜索</el-button>
          </template>
        </el-input>

        <div v-if="mcpSearchResults.length > 0" class="mcp-results">
          <div class="mcp-results-header">
            <span>搜索结果（{{ mcpSearchResults.length }} 篇）</span>
            <el-button size="small" type="primary" @click="selectAllMcp">全选</el-button>
          </div>
          <div class="mcp-results-list">
            <div
              v-for="(article, i) in mcpSearchResults"
              :key="i"
              class="mcp-article-item"
              :class="{ selected: mcpSelectedIndices.has(i) }"
              @click="toggleMcpSelect(i)"
            >
              <div class="mcp-article-title">{{ article.title || article.articleTitle || `文章 ${i + 1}` }}</div>
              <div class="mcp-article-meta">
                <span v-if="article.source || article.mediaName">{{ article.source || article.mediaName }}</span>
                <span v-if="article.publishTime || article.publishDate">{{ article.publishTime || article.publishDate }}</span>
              </div>
              <div v-if="article.summary || article.content" class="mcp-article-summary">
                {{ (article.summary || article.content || '').substring(0, 120) }}...
              </div>
            </div>
          </div>
        </div>
        <el-empty v-else-if="!mcpSearching" description="输入关键词搜索舆情文章" :image-size="60" />
      </div>
      <template #footer>
        <el-button @click="showMcpDialog = false">取消</el-button>
        <el-button type="primary" :disabled="mcpSelectedIndices.size === 0" @click="confirmMcpImport">
          确认引入（{{ mcpSelectedIndices.size }} 篇）
        </el-button>
      </template>
    </el-dialog>

    <!-- Web 搜索弹窗（Tavily） -->
    <el-dialog v-model="showWebSearchDialog" title="Web 搜索（Tavily）" width="700px" top="6vh">
      <div class="mcp-dialog-body">
        <el-input
          v-model="webSearchQuery"
          placeholder="搜索政策原文、行业报告、技术文档..."
          @keyup.enter="handleWebSearch"
        >
          <template #append>
            <el-button @click="handleWebSearch" :loading="webSearching">搜索</el-button>
          </template>
        </el-input>
        <div v-if="webSearchResults.length > 0" class="mcp-results">
          <div class="mcp-results-header">
            <span>搜索结果（{{ webSearchResults.length }} 条）</span>
          </div>
          <div class="mcp-results-list">
            <div
              v-for="(item, i) in webSearchResults"
              :key="i"
              class="mcp-article-item"
              :class="{ selected: webSelectedIndices.has(i) }"
              @click="toggleWebSelect(i)"
            >
              <div class="mcp-article-title">{{ item.title || `结果 ${i + 1}` }}</div>
              <div class="mcp-article-meta">
                <a v-if="item.url" :href="item.url" target="_blank" class="web-url">{{ item.url }}</a>
              </div>
              <div v-if="item.content || item.snippet" class="mcp-article-summary">
                {{ (item.content || item.snippet || '').substring(0, 200) }}...
              </div>
            </div>
          </div>
        </div>
        <el-empty v-else-if="!webSearching" description="输入关键词搜索互联网" :image-size="60" />
      </div>
      <template #footer>
        <el-button @click="showWebSearchDialog = false">取消</el-button>
        <el-button type="primary" :disabled="webSelectedIndices.size === 0" @click="confirmWebImport">
          确认引入（{{ webSelectedIndices.size }} 条）
        </el-button>
      </template>
    </el-dialog>

    <!-- URL 抓取弹窗 -->
    <el-dialog v-model="showFetchDialog" title="URL 内容抓取" width="700px" top="6vh">
      <div class="mcp-dialog-body">
        <el-input
          v-model="fetchUrlInput"
          placeholder="输入网页 URL，如 https://example.com/report"
          @keyup.enter="handleFetchUrl"
        >
          <template #append>
            <el-button @click="handleFetchUrl" :loading="fetching">抓取</el-button>
          </template>
        </el-input>
        <div v-if="fetchResult" class="fetch-result">
          <div class="mcp-results-header">
            <span>抓取结果（{{ fetchResult.length }} 字符）</span>
            <el-button size="small" type="primary" @click="confirmFetchImport">引入到知识库</el-button>
          </div>
          <div class="fetch-content">{{ fetchResult.substring(0, 1000) }}{{ fetchResult.length > 1000 ? '...' : '' }}</div>
        </div>
        <el-empty v-else-if="!fetching" description="输入 URL 抓取网页内容" :image-size="60" />
      </div>
    </el-dialog>

    <!-- Right: Traceability + Quality Panel -->
    <div v-if="currentReportId && content" class="citations-panel">
      <el-card class="citations-card">
        <el-tabs v-model="rightTab" class="right-tabs">
          <!-- Tab 1: 引用溯源 -->
          <el-tab-pane name="citations">
            <template #label>
              <span class="tab-label">
                <el-icon><Collection /></el-icon>
                引用溯源
                <el-tag v-if="chunks.length" size="small" type="info" effect="light">
                  {{ chunks.length }}
                </el-tag>
              </span>
            </template>
            <div v-if="!chunks.length" class="empty-tab">
              <el-empty description="本次生成未调用知识库，暂无溯源" :image-size="80" />
            </div>
            <div v-else class="citations-list">
              <div
                v-for="c in chunks"
                :key="c.index"
                :id="`cite-card-${c.index}`"
                class="citation-card"
                :class="{ highlighted: highlightedCite === c.index }"
              >
                <div class="citation-head">
                  <span class="citation-idx">[{{ c.index }}]</span>
                  <span class="citation-icon" :title="c.fileType">{{ fileIcon(c.fileType) }}</span>
                  <span class="citation-file" :title="c.filename">{{ c.filename }}</span>
                  <el-tag v-if="pageLabel(c)" size="small" type="warning" effect="plain">
                    {{ pageLabel(c) }}
                  </el-tag>
                  <el-tag size="small" effect="plain">#{{ c.chunkIndex }}</el-tag>
                </div>
                <div class="citation-body">{{ c.content }}</div>
                <div class="citation-foot">
                  <div class="citation-score">
                    <div class="score-bar">
                      <div class="score-fill" :style="{ width: Math.min(c.score * 100, 100) + '%' }"></div>
                    </div>
                    <span class="score-text">相关度 {{ (c.score * 100).toFixed(0) }}%</span>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <!-- Tab 2: 质量检查（赛题 3.4） -->
          <el-tab-pane name="quality">
            <template #label>
              <span class="tab-label">
                <el-icon><Aim /></el-icon>
                质量检查
                <el-tag v-if="qualityReport?.overallScore != null"
                        size="small"
                        :type="qualityTagType(qualityReport.overallScore)"
                        effect="light">
                  {{ qualityReport.overallScore }}
                </el-tag>
              </span>
            </template>

            <div v-if="!qualityReport && !qualityLoading" class="empty-tab">
              <el-empty description="一键检查覆盖度 / 引用准确性 / 事实性" :image-size="80">
                <el-button type="primary" :disabled="generating || rewriting"
                           @click="runQualityCheck">
                  <el-icon><MagicStick /></el-icon>
                  运行三维度检查
                </el-button>
              </el-empty>
            </div>

            <div v-else-if="qualityLoading" class="empty-tab">
              <el-icon class="is-loading" style="font-size: 32px; color: #6366f1"><Loading /></el-icon>
              <div style="margin-top: 12px; color: #64748b">AI 正在审阅报告…</div>
            </div>

            <div v-else-if="qualityReport" class="quality-result">
              <div class="quality-head">
                <div class="quality-score-block">
                  <span class="quality-score">{{ qualityReport.overallScore ?? '—' }}</span>
                  <span class="quality-score-label">综合评分</span>
                </div>
                <div class="quality-summary">{{ qualityReport.summary || '无总评' }}</div>
              </div>

              <div class="quality-dims">
                <div class="quality-dim">
                  <div class="quality-dim-head">
                    <span class="quality-dim-title">📋 覆盖度</span>
                    <span class="quality-dim-score">
                      {{ formatPct(qualityReport.coverageScore) }}
                    </span>
                  </div>
                  <ul v-if="qualityReport.missingKeyPoints?.length" class="quality-list">
                    <li v-for="(item, i) in qualityReport.missingKeyPoints" :key="i">{{ item }}</li>
                  </ul>
                  <div v-else class="quality-empty">全部关键要点均已覆盖 ✓</div>
                </div>

                <div class="quality-dim">
                  <div class="quality-dim-head">
                    <span class="quality-dim-title">🔗 引用准确性</span>
                    <span class="quality-dim-score">
                      {{ formatPct(qualityReport.citationAccuracyScore) }}
                    </span>
                  </div>
                  <ul v-if="qualityReport.citationIssues?.length" class="quality-list">
                    <li v-for="(issue, i) in qualityReport.citationIssues" :key="i">
                      <code class="cite-badge">[{{ issue.citedIndex }}]</code>
                      <span class="issue-sentence">{{ issue.sentence }}</span>
                      <div class="issue-reason">{{ issue.reason }}</div>
                    </li>
                  </ul>
                  <div v-else class="quality-empty">未发现可疑引用 ✓</div>
                </div>

                <div class="quality-dim">
                  <div class="quality-dim-head">
                    <span class="quality-dim-title">✅ 事实性</span>
                    <span class="quality-dim-score">
                      {{ formatPct(qualityReport.factualityScore) }}
                    </span>
                  </div>
                  <ul v-if="qualityReport.factualityIssues?.length" class="quality-list">
                    <li v-for="(issue, i) in qualityReport.factualityIssues" :key="i">
                      <el-tag size="small" :type="suggestionTagType(issue.suggestion)" effect="plain">
                        {{ suggestionLabel(issue.suggestion) }}
                      </el-tag>
                      <span class="issue-sentence">{{ issue.sentence }}</span>
                      <div class="issue-reason">{{ issue.reason }}</div>
                    </li>
                  </ul>
                  <div v-else class="quality-empty">未发现无源杜撰 ✓</div>
                </div>
              </div>

              <div class="quality-actions">
                <el-button size="small" :disabled="qualityLoading" @click="runQualityCheck">
                  <el-icon><Refresh /></el-icon>
                  重新检查
                </el-button>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>

    <!-- 角标 hover 预览浮层（teleport 到 body 避免被 el-card overflow 切掉） -->
    <Teleport to="body">
      <transition name="cite-pop">
        <div
          v-if="citePopover"
          class="cite-popover"
          :style="{ left: citePopover.x + 'px', top: citePopover.y + 'px' }"
          @mouseenter="cancelHideCitePopover"
          @mouseleave="scheduleHideCitePopover"
        >
          <div class="cite-pop-head">
            <span class="cite-pop-idx">[{{ citePopover.chunk.index }}]</span>
            <span class="cite-pop-icon">{{ fileIcon(citePopover.chunk.fileType) }}</span>
            <span class="cite-pop-file" :title="citePopover.chunk.filename">
              {{ citePopover.chunk.filename }}
            </span>
            <el-tag v-if="pageLabel(citePopover.chunk)" size="small" type="warning" effect="plain">
              {{ pageLabel(citePopover.chunk) }}
            </el-tag>
          </div>
          <div class="cite-pop-body">{{ citePopover.chunk.content }}</div>
          <div class="cite-pop-foot">
            <span class="cite-pop-score">相关度 {{ (citePopover.chunk.score * 100).toFixed(0) }}%</span>
            <span class="cite-pop-hint">点击角标可跳转侧栏卡片</span>
          </div>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import {
  ElMessage,
  ElMessageBox
} from 'element-plus'
import {
  EditPen,
  Document,
  MagicStick,
  Loading,
  CopyDocument,
  Select,
  ArrowDown,
  Collection,
  Download,
  Connection,
  Check,
  Search,
  Link,
  InfoFilled,
  ChatDotRound,
  Switch,
  SetUp,
  Promotion,
  Aim,
  Refresh
} from '@element-plus/icons-vue'
import { getKnowledgeBases, type KnowledgeBase } from '@/api/knowledge'
import {
  getTemplates,
  createReport,
  updateReport,
  mcpSearchArticles,
  tavilySearch,
  fetchUrl as fetchUrlApi,
  checkReportQuality,
  type Template,
  type RewriteMode,
  type QualityReport
} from '@/api/report'
import { useUserStore } from '@/stores/user'
import { renderReportMarkdown } from '@/utils/markdown'

/** RAG 命中条目（后端 SSE chunks 事件的 payload 元素）。index 从 1 起，对应正文 [n]。 */
interface ChunkHit {
  index: number
  id: number | string
  filename: string
  /** 源文件 MIME（如 application/pdf / text/html）。前端用它决定图标。 */
  fileType?: string
  chunkIndex: number
  /** PDF 才有的起止页码（1-based）。非 PDF 为 null / 缺省。 */
  pageStart?: number | null
  pageEnd?: number | null
  content: string
  score: number
}

/** 根据 fileType 选溯源卡的 emoji 图标。 */
function fileIcon(fileType?: string): string {
  if (!fileType) return '📄'
  if (fileType.includes('pdf')) return '📕'
  if (fileType.includes('word') || fileType.includes('officedocument.wordprocessingml')) return '📘'
  if (fileType.includes('html')) return '🌐'
  if (fileType.includes('markdown') || fileType.includes('text/plain')) return '📝'
  return '📄'
}

/** 把 page_start/page_end 渲染成"第 3 页"/"第 3-5 页"/空字符串。 */
function pageLabel(c: ChunkHit): string {
  if (c.pageStart == null) return ''
  if (c.pageEnd == null || c.pageEnd === c.pageStart) return `第 ${c.pageStart} 页`
  return `第 ${c.pageStart}-${c.pageEnd} 页`
}

const userStore = useUserStore()

const form = ref<{
  title: string
  topic: string
  keyPoints: string
  kbId: number | null
  templateId: number | null
  generationDepth: 'brief' | 'standard' | 'deep'
}>({
  title: '',
  topic: '',
  keyPoints: '',
  kbId: null,
  templateId: null,
  generationDepth: 'standard'
})

const content = ref('')
const generating = ref(false)
const rewriting = ref(false)
const saving = ref(false)
const exporting = ref(false)
const currentReportId = ref<number | null>(null)
const knowledgeBases = ref<KnowledgeBase[]>([])
const templates = ref<Template[]>([])

// 引用溯源状态 —— 后端通过 SSE chunks 事件推送 RAG top-k 命中列表。
const chunks = ref<ChunkHit[]>([])
const highlightedCite = ref<number | null>(null)

// 角标 hover 预览浮层（Granola 式），用 teleport 到 body 以脱离 overflow 截断
const citePopover = ref<{ chunk: ChunkHit, x: number, y: number } | null>(null)
let citeShowTimer: number | null = null
let citeHideTimer: number | null = null

// 右侧面板的 tab：citations | quality
const rightTab = ref<'citations' | 'quality'>('citations')

// 质量检查状态
const qualityReport = ref<QualityReport | null>(null)
const qualityLoading = ref(false)

// MCP 舆情数据引入状态
const showMcpDialog = ref(false)
const mcpKeyword = ref('')
const mcpSearching = ref(false)
const mcpSearchResults = ref<any[]>([])
const mcpSelectedIndices = ref<Set<number>>(new Set())
const mcpArticles = ref<any[]>([])

const showWebSearchDialog = ref(false)
const webSearchQuery = ref('')
const webSearching = ref(false)
const webSearchResults = ref<any[]>([])
const webSelectedIndices = ref<Set<number>>(new Set())
const showFetchDialog = ref(false)
const fetchUrlInput = ref('')
const fetching = ref(false)
const fetchResult = ref<string>('')

const includeKeywords = ref<string[]>([])
const excludeKeywords = ref<string[]>([])
const showIncludeInput = ref(false)
const showExcludeInput = ref(false)
const includeInputVal = ref('')
const excludeInputVal = ref('')
const includeInputRef = ref<InstanceType<typeof import('element-plus')['ElInput']> | null>(null)
const excludeInputRef = ref<InstanceType<typeof import('element-plus')['ElInput']> | null>(null)

function addIncludeKeyword() {
  const v = includeInputVal.value.trim()
  if (v && !includeKeywords.value.includes(v)) includeKeywords.value.push(v)
  includeInputVal.value = ''
  showIncludeInput.value = false
}

function addExcludeKeyword() {
  const v = excludeInputVal.value.trim()
  if (v && !excludeKeywords.value.includes(v)) excludeKeywords.value.push(v)
  excludeInputVal.value = ''
  showExcludeInput.value = false
}

// AI 生成进度
const progressSteps = ['检索知识库', '分析风格与结构', '获取舆情数据', 'AI 撰写报告', '完稿与版本保存']
const progressStep = ref<{ step: string; stepIndex: number; totalSteps: number } | null>(null)

// 编辑 / 预览切换。生成/改写过程中默认编辑态（让用户看流式 token），完成后跳到预览态以显示角标。
const viewMode = ref<'edit' | 'preview'>('edit')
const renderedSectionHtml = computed(() => renderReportMarkdown(content.value, true))
const streamingHtml = computed(() => {
  if (!content.value) return ''
  const html = renderReportMarkdown(content.value)
  return html + '<span class="typing-cursor">▍</span>'
})

const previewEl = ref<HTMLDivElement | null>(null)
const streamingEl = ref<HTMLDivElement | null>(null)

const activeSectionId = ref(-1)
const sectionRewriting = ref(false)
const showAiChat = ref(false)
const aiChatMessages = ref<{ role: string; content: string }[]>([])
const aiChatInput = ref('')
const aiChatLoading = ref(false)

const aiSuggestions = [
  '帮我润色这段文字',
  '扩写当前章节',
  '总结报告要点',
  '生成结论段落',
  '翻译为英文',
  '优化报告结构'
]

// In-flight abort controller for streaming requests
let activeController: AbortController | null = null

const wordCount = computed(() => content.value.length)
const canGenerate = computed(
  () => !generating.value && !!form.value.title.trim() && !!form.value.topic.trim()
)

const selectedTemplateDesc = computed(() => {
  if (!form.value.templateId) return ''
  const t = templates.value.find(t => t.id === form.value.templateId)
  return t?.description || ''
})

onMounted(async () => {
  try {
    const [kbRes, tmplRes] = await Promise.all([
      getKnowledgeBases(),
      getTemplates()
    ])
    const kbRaw = (kbRes as any).data
    knowledgeBases.value = Array.isArray(kbRaw) ? kbRaw : Array.isArray(kbRaw?.records) ? kbRaw.records : []
    const tmplRaw = (tmplRes as any).data
    templates.value = Array.isArray(tmplRaw) ? tmplRaw : Array.isArray(tmplRaw?.records) ? tmplRaw.records : []
  } catch (e) {
    console.error('加载知识库/模板失败:', e)
  }
})

onBeforeUnmount(() => {
  // Cancel any in-flight SSE stream on unmount to avoid leaks
  activeController?.abort()
})

interface SseHandlers {
  onToken?: (payload: string) => void
  onChunks?: (hits: ChunkHit[]) => void
  onProgress?: (step: string, stepIndex: number, totalSteps: number) => void
  onDone?: (payload: string) => void
  onError?: (msg: string) => void
}

/**
 * 解析 fetch Response 上的 SSE 流，按 `event:` 名称分发：
 *   - token  → onToken（正文 token，默认追加到 content）
 *   - chunks → onChunks（RAG 命中列表 JSON）
 *   - done   → onDone（结束信号，同时会令读取循环退出）
 *   - error  → onError（后端在生成过程中抛错）
 *
 * 关键要点：
 *   1. TextDecoder 开启 stream 模式，保证跨网络包的多字节中文字符不被截断；
 *   2. 每帧以空行（\n\n）结尾，拆分后把不完整的尾部留在 buffer；
 *   3. data 行支持同帧多条，按 \n 连接（与 SSE 规范一致）；
 *   4. 同一帧只会有一个 event 名；未声明 event 时按 "message" 处理，我们当作 token。
 */
async function consumeSseStream(response: Response, handlers: SseHandlers): Promise<void> {
  if (!response.ok || !response.body) {
    throw new Error(`HTTP ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let finished = false

  const dispatchFrame = (frame: string) => {
    let eventName = 'message'
    const dataParts: string[] = []
    for (const rawLine of frame.split('\n')) {
      if (!rawLine || rawLine.startsWith(':')) continue // keepalive / 注释
      if (rawLine.startsWith('event:')) {
        eventName = rawLine.slice(6).trim()
      } else if (rawLine.startsWith('data:')) {
        dataParts.push(rawLine.slice(5).replace(/^ /, ''))
      }
    }
    const data = dataParts.join('\n')
    switch (eventName) {
      case 'chunks': {
        if (!handlers.onChunks) return
        try {
          const parsed = JSON.parse(data) as ChunkHit[]
          handlers.onChunks(Array.isArray(parsed) ? parsed : [])
        } catch (e) {
          console.warn('SSE chunks payload parse failed:', e)
        }
        return
      }
      case 'progress': {
        if (!handlers.onProgress) return
        try {
          const parsed = JSON.parse(data)
          handlers.onProgress(parsed.step, parsed.stepIndex, parsed.totalSteps)
        } catch (e) {
          console.warn('SSE progress payload parse failed:', e)
        }
        return
      }
      case 'done':
        finished = true
        handlers.onDone?.(data)
        return
      case 'error':
        finished = true
        handlers.onError?.(data)
        return
      case 'token':
      case 'message':
      default:
        // 兜底：未知 event 也当 token 处理，避免漏掉内容
        if (data) handlers.onToken?.(data)
    }
  }

  try {
    while (!finished) {
      const { done: readerDone, value } = await reader.read()
      if (readerDone) break

      buffer += decoder.decode(value, { stream: true })
      const frames = buffer.split(/\n\n/)
      buffer = frames.pop() ?? ''

      for (const frame of frames) {
        if (!frame.trim()) continue
        dispatchFrame(frame)
        if (finished) break
      }
    }

    // Flush 最后一帧（没有尾随空行）
    if (!finished && buffer.trim()) dispatchFrame(buffer)
  } finally {
    try {
      reader.releaseLock()
    } catch (_) {
      /* noop */
    }
  }
}

async function handleGenerate() {
  if (!canGenerate.value) return

  generating.value = true
  content.value = ''
  chunks.value = []
  qualityReport.value = null // 新报告产生，旧的质检结果就过时了
  rightTab.value = 'citations'
  currentReportId.value = null
  // 生成期保持 edit 态，方便用户看 token 流；完成后再切到 preview。
  viewMode.value = 'edit'

  const controller = new AbortController()
  activeController = controller

  let streamError: string | null = null

  try {
    // 1. Create draft report first to get an ID
    // 后端 ReportCreateDTO.keyPoints 是 List<String>；按行切 + trim + 去空；空则不发字段（Jackson 不会把空串硬塞成 List）
    const kpArr = (form.value.keyPoints || '')
      .split(/\r?\n/)
      .map((s) => s.trim())
      .filter(Boolean)
    const createRes = await createReport({
      title: form.value.title.trim(),
      topic: form.value.topic.trim(),
      ...(kpArr.length ? { keyPoints: kpArr } : {}),
      kbId: form.value.kbId,
      templateId: form.value.templateId,
      // 赛题 2.3：把用户手填的补充/排除关键词一起下沉到后端 BOOLEAN query
      includeKeywords: includeKeywords.value.length ? includeKeywords.value.join(' ') : undefined,
      excludeKeywords: excludeKeywords.value.length ? excludeKeywords.value.join(' ') : undefined,
      generationDepth: form.value.generationDepth
    })
    const draft = (createRes as any).data as { id: number } | null
    if (!draft?.id) {
      throw new Error('创建报告草稿失败')
    }
    currentReportId.value = draft.id

    // 2. Open SSE stream via fetch (EventSource cannot send Authorization header)
    const token = userStore.token || localStorage.getItem('token') || ''
    const resp = await fetch(`/api/v1/reports/${draft.id}/generate`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'text/event-stream'
      },
      signal: controller.signal
    })

    if (!resp.ok) {
      throw new Error(`生成接口返回 ${resp.status}`)
    }

    await consumeSseStream(resp, {
      onToken: (t) => {
        content.value += t
        nextTick(() => {
          if (streamingEl.value) {
            streamingEl.value.scrollTop = streamingEl.value.scrollHeight
          }
        })
      },
      onChunks: (hits) => { chunks.value = hits },
      onProgress: (step, stepIndex, totalSteps) => {
        progressStep.value = { step, stepIndex, totalSteps }
      },
      onError: (msg) => { streamError = msg || '生成失败' }
    })

    if (streamError) throw new Error(streamError)

    ElMessage.success('报告生成完成')
    // 有正文之后默认切到预览态，用户第一眼就能看到角标效果。
    if (content.value) viewMode.value = 'preview'
  } catch (e: any) {
    if (e?.name === 'AbortError') {
      ElMessage.info('已取消生成')
    } else {
      console.error('生成失败:', e)
      ElMessage.error(e?.message || '生成失败，请重试')
    }
  } finally {
    generating.value = false
    progressStep.value = null
    activeController = null
  }
}

async function handleRewrite(mode: RewriteMode) {
  if (!currentReportId.value || !content.value) return

  let instruction: string | undefined

  if (mode === 'DATA_UPDATE') {
    try {
      const { value } = await ElMessageBox.prompt(
        '请输入数据更新指令（例如：把 2024 年数据换成 2025 年 / 更新市场规模到最新季度）',
        '数据更新',
        {
          confirmButtonText: '开始改写',
          cancelButtonText: '取消',
          inputType: 'textarea',
          inputPlaceholder: '请描述要替换/更新的数据...',
          inputValidator: (v) => (v && v.trim().length > 0) || '请输入数据更新指令'
        }
      )
      instruction = value
    } catch {
      return
    }
  } else if (mode === 'ANGLE_SHIFT') {
    try {
      const { value } = await ElMessageBox.prompt(
        '请输入视角调整指令（例如：面向领导的简报风格 / 从企业视角切换到行业监管视角）',
        '视角调整',
        {
          confirmButtonText: '开始改写',
          cancelButtonText: '取消',
          inputType: 'textarea',
          inputPlaceholder: '请描述目标视角 / 受众...',
          inputValidator: (v) => (v && v.trim().length > 0) || '请输入视角指令'
        }
      )
      instruction = value
    } catch {
      return
    }
  } else if (mode === 'CONTINUATION') {
    try {
      const { value } = await ElMessageBox.prompt(
        '请描述要续写的新章节（例如：补一章"风险与对策" / 延伸讨论 2026 年趋势）',
        '续写新章节',
        {
          confirmButtonText: '开始续写',
          cancelButtonText: '取消',
          inputType: 'textarea',
          inputPlaceholder: '新章节的主题或要点...',
          inputValidator: (v) => (v && v.trim().length > 0) || '请输入新章节主题'
        }
      )
      instruction = value
    } catch {
      return
    }
  }

  rewriting.value = true
  const originalContent = content.value
  qualityReport.value = null // 改写后旧质检结果过时
  // CONTINUATION：原稿保留不变，SSE 流出来的新章节追加到末尾；其他模式清空后接收整稿。
  const isContinuation = mode === 'CONTINUATION'
  if (!isContinuation) content.value = ''
  // 改写期同样先回到编辑态以便查看 token；完成后若仍有正文再切回预览。
  viewMode.value = 'edit'

  const controller = new AbortController()
  activeController = controller

  let streamError: string | null = null

  try {
    const token = userStore.token || localStorage.getItem('token') || ''
    const resp = await fetch(`/api/v1/reports/${currentReportId.value}/rewrite`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        Accept: 'text/event-stream'
      },
      body: JSON.stringify({
        mode,
        targetParagraph: originalContent,
        instruction
      }),
      signal: controller.signal
    })

    if (!resp.ok) {
      throw new Error(`改写接口返回 ${resp.status}`)
    }

    // 续写：首个 token 到达前先插一个段落分隔，避免新章节与原稿末行粘连。
    let continuationSeparatorInserted = false

    await consumeSseStream(resp, {
      onToken: (t) => {
        if (isContinuation && !continuationSeparatorInserted) {
          if (!content.value.endsWith('\n\n')) {
            content.value += content.value.endsWith('\n') ? '\n' : '\n\n'
          }
          continuationSeparatorInserted = true
        }
        content.value += t
        nextTick(() => {
          if (streamingEl.value) {
            streamingEl.value.scrollTop = streamingEl.value.scrollHeight
          }
        })
      },
      onChunks: (hits) => { chunks.value = hits },
      onError: (msg) => { streamError = msg || '改写失败' }
    })

    if (streamError) throw new Error(streamError)

    ElMessage.success('改写完成')
    if (content.value) viewMode.value = 'preview'
  } catch (e: any) {
    // Roll back to original on failure
    content.value = originalContent
    if (e?.name === 'AbortError') {
      ElMessage.info('已取消改写')
    } else {
      console.error('改写失败:', e)
      ElMessage.error(e?.message || '改写失败，请重试')
    }
  } finally {
    rewriting.value = false
    activeController = null
  }
}

/**
 * 正文 `[n]` 角标点击 → 滚动到右侧溯源卡 + 短暂高亮。
 * 用事件委托：markdown 是 v-html 渲染的，Vue 的 @click 绑不到动态 sup 上。
 */
function handleCiteClick(e: MouseEvent) {
  const target = e.target as HTMLElement | null
  if (!target) return
  const sup = target.closest('sup.cite') as HTMLElement | null
  if (!sup) return
  const idxStr = sup.dataset.idx
  if (!idxStr) return
  const idx = Number(idxStr)
  if (!Number.isFinite(idx)) return
  scrollToCitation(idx)
}

function scrollToCitation(n: number) {
  // 点击角标优先切到"引用溯源"tab；tab 切换后 DOM 才可见，用 nextTick 等一下再 scroll。
  if (rightTab.value !== 'citations') rightTab.value = 'citations'
  nextTick(() => {
    const el = document.getElementById(`cite-card-${n}`)
    if (!el) {
      ElMessage.warning(`未找到第 ${n} 条引用`)
      return
    }
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    highlightedCite.value = n
    // 1.5s 后撤销高亮；若期间又点了别的则不动它
    window.setTimeout(() => {
      if (highlightedCite.value === n) highlightedCite.value = null
    }, 1500)
  })
}

// -------- 角标 hover 预览浮层（Granola 式） --------

function scheduleCitePopover(sup: HTMLElement) {
  if (citeHideTimer) { clearTimeout(citeHideTimer); citeHideTimer = null }
  const idxStr = sup.dataset.idx
  if (!idxStr) return
  const idx = Number(idxStr)
  const chunk = chunks.value.find(c => c.index === idx)
  if (!chunk) return
  // 已经显示同一个就不重排位置，避免抖动
  if (citePopover.value && citePopover.value.chunk.index === idx) return
  if (citeShowTimer) clearTimeout(citeShowTimer)
  citeShowTimer = window.setTimeout(() => {
    const rect = sup.getBoundingClientRect()
    // 锚点放在 sup 下方 8px；水平靠近左缘但不超出窗口右边界
    const popoverMaxWidth = 360
    const x = Math.min(
      rect.left + window.scrollX,
      window.innerWidth + window.scrollX - popoverMaxWidth - 12
    )
    const y = rect.bottom + window.scrollY + 8
    citePopover.value = { chunk, x, y }
  }, 200)
}

function scheduleHideCitePopover() {
  if (citeShowTimer) { clearTimeout(citeShowTimer); citeShowTimer = null }
  if (citeHideTimer) clearTimeout(citeHideTimer)
  citeHideTimer = window.setTimeout(() => {
    citePopover.value = null
  }, 150)
}

function cancelHideCitePopover() {
  if (citeHideTimer) { clearTimeout(citeHideTimer); citeHideTimer = null }
}

// -------- 质量检查（赛题 3.4） --------

async function runQualityCheck() {
  if (!currentReportId.value) return
  qualityLoading.value = true
  try {
    const res = await checkReportQuality(currentReportId.value)
    qualityReport.value = (res as any).data as QualityReport
  } catch (e: any) {
    console.error('质量检查失败:', e)
    ElMessage.error(e?.message || '质量检查失败，请稍后重试')
  } finally {
    qualityLoading.value = false
  }
}

function formatPct(v: number | null | undefined): string {
  if (v == null || isNaN(v as number)) return '—'
  return Math.round(v * 100) + '%'
}

function qualityTagType(score: number | null | undefined): 'success' | 'warning' | 'danger' {
  if (score == null) return 'warning'
  if (score >= 80) return 'success'
  if (score >= 60) return 'warning'
  return 'danger'
}

function suggestionLabel(s: string | undefined): string {
  switch (s) {
    case 'mark': return '标待核实'
    case 'fix': return '修正数据'
    case 'soften': return '弱化语气'
    default: return s || '待处理'
  }
}

function suggestionTagType(s: string | undefined): 'info' | 'warning' | 'danger' {
  if (s === 'fix') return 'danger'
  if (s === 'soften') return 'warning'
  return 'info'
}

async function searchMcpArticles() {
  if (!mcpKeyword.value.trim()) return
  mcpSearching.value = true
  mcpSearchResults.value = []
  mcpSelectedIndices.value = new Set()
  try {
    const res = await mcpSearchArticles(mcpKeyword.value.trim())
    const data = (res as any).data
    if (data && typeof data === 'object') {
      if (Array.isArray(data)) {
        mcpSearchResults.value = data
      } else if (data.list && Array.isArray(data.list)) {
        mcpSearchResults.value = data.list
      } else if (data.data && Array.isArray(data.data)) {
        mcpSearchResults.value = data.data
      } else if (data.records && Array.isArray(data.records)) {
        mcpSearchResults.value = data.records
      } else {
        mcpSearchResults.value = [data]
      }
    }
  } catch (e) {
    console.error('MCP 搜索失败:', e)
    ElMessage.error('搜索舆情数据失败')
  } finally {
    mcpSearching.value = false
  }
}

function toggleMcpSelect(index: number) {
  const newSet = new Set(mcpSelectedIndices.value)
  if (newSet.has(index)) {
    newSet.delete(index)
  } else {
    newSet.add(index)
  }
  mcpSelectedIndices.value = newSet
}

function selectAllMcp() {
  if (mcpSelectedIndices.value.size === mcpSearchResults.value.length) {
    mcpSelectedIndices.value = new Set()
  } else {
    mcpSelectedIndices.value = new Set(mcpSearchResults.value.map((_, i) => i))
  }
}

function confirmMcpImport() {
  const selected = mcpSelectedIndices.value
  const articles = Array.from(selected).map(i => mcpSearchResults.value[i])
  mcpArticles.value = [...mcpArticles.value, ...articles]
  showMcpDialog.value = false
  ElMessage.success(`已引入 ${articles.length} 篇舆情文章`)
}

async function handleWebSearch() {
  if (!webSearchQuery.value.trim()) return
  webSearching.value = true
  webSearchResults.value = []
  webSelectedIndices.value = new Set()
  try {
    const res = await tavilySearch(webSearchQuery.value.trim(), 10)
    const data = (res as any).data
    if (data?.results && Array.isArray(data.results)) {
      webSearchResults.value = data.results
    } else if (Array.isArray(data)) {
      webSearchResults.value = data
    }
  } catch (e) {
    console.error('Web 搜索失败:', e)
    ElMessage.error('Web 搜索失败')
  } finally {
    webSearching.value = false
  }
}

function toggleWebSelect(index: number) {
  const newSet = new Set(webSelectedIndices.value)
  if (newSet.has(index)) newSet.delete(index)
  else newSet.add(index)
  webSelectedIndices.value = newSet
}

function confirmWebImport() {
  const selected = webSelectedIndices.value
  const items = Array.from(selected).map(i => webSearchResults.value[i])
  mcpArticles.value = [...mcpArticles.value, ...items]
  showWebSearchDialog.value = false
  ElMessage.success(`已引入 ${items.length} 条 Web 搜索结果`)
}

async function handleFetchUrl() {
  if (!fetchUrlInput.value.trim()) return
  fetching.value = true
  fetchResult.value = ''
  try {
    const res = await fetchUrlApi(fetchUrlInput.value.trim())
    const data = (res as any).data
    fetchResult.value = data?.content || data?.text || ''
  } catch (e) {
    console.error('URL 抓取失败:', e)
    ElMessage.error('URL 抓取失败')
  } finally {
    fetching.value = false
  }
}

function confirmFetchImport() {
  if (!fetchResult.value) return
  mcpArticles.value = [...mcpArticles.value, { title: fetchUrlInput.value, content: fetchResult.value, source: 'fetch' }]
  showFetchDialog.value = false
  ElMessage.success('已引入 URL 内容')
}

function handleSectionHover(e: MouseEvent) {
  const target = e.target as HTMLElement | null
  if (!target) return
  const block = target.closest('.section-block') as HTMLElement | null
  if (block) {
    activeSectionId.value = Number(block.dataset.sectionId ?? -1)
  }
  // 引用角标的 hover 预览（Granola 式）：mouseover 事件冒泡路径里如果有 sup.cite，
  // 延迟 200ms 后打开浮层 —— 避免光标掠过就闪。
  const sup = target.closest('sup.cite') as HTMLElement | null
  if (sup) scheduleCitePopover(sup)
}

function handlePreviewMouseout(e: MouseEvent) {
  activeSectionId.value = -1
  // 只在光标真正离开 sup 区域时调度隐藏（relatedTarget 依然在 sup 内就不动）
  const sup = (e.target as HTMLElement | null)?.closest('sup.cite')
  if (!sup) return
  const to = e.relatedTarget as HTMLElement | null
  if (to && (to.closest('sup.cite') === sup || to.closest('.cite-popover'))) return
  scheduleHideCitePopover()
}

function handlePreviewClick(e: MouseEvent) {
  const target = e.target as HTMLElement | null
  if (!target) return

  const actionBtn = target.closest('.section-action-btn') as HTMLElement | null
  if (actionBtn) {
    const mode = actionBtn.dataset.mode
    const block = actionBtn.closest('.section-block') as HTMLElement | null
    if (block && mode) {
      handleSectionRewrite(block, mode)
    }
    return
  }

  handleCiteClick(e)
}

async function handleSectionRewrite(block: HTMLElement, mode: string) {
  if (!currentReportId.value || sectionRewriting.value) return
  const sectionText = block.textContent?.trim() || ''
  if (!sectionText) return

  sectionRewriting.value = true
  const modeLabels: Record<string, string> = {
    rewrite: '改写',
    expand: '扩写',
    condense: '精简'
  }

  try {
    const resp = await fetch(`/api/v1/reports/${currentReportId.value}/rewrite-section`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: JSON.stringify({ content: sectionText, mode, instruction: '' })
    })

    if (!resp.ok) throw new Error('段落改写请求失败')

    const reader = resp.body?.getReader()
    if (!reader) throw new Error('无法读取流')

    const decoder = new TextDecoder()
    let newSection = ''
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:token')) continue
        if (line.startsWith('data:')) {
          const data = line.slice(5)
          if (data) newSection += data
        }
      }
    }

    if (newSection.trim()) {
      content.value = content.value.replace(sectionText, newSection.trim())
      ElMessage.success(`段落${modeLabels[mode] || mode}完成`)
    }
  } catch (e) {
    console.error('段落改写失败:', e)
    ElMessage.error('段落改写失败')
  } finally {
    sectionRewriting.value = false
  }
}

async function saveReport() {
  if (!currentReportId.value) return
  saving.value = true
  try {
    await updateReport(currentReportId.value, {
      title: form.value.title,
      content: content.value
    })
    ElMessage.success('保存成功')
  } catch (e) {
    console.error('保存失败:', e)
  } finally {
    saving.value = false
  }
}

function handleEditorKeydown(e: KeyboardEvent | Event) {
  if (e instanceof KeyboardEvent && e.ctrlKey && e.key === 'Enter') {
    e.preventDefault()
    showAiChat.value = true
  }
}

function handleTranslate() {
  if (!content.value) return
  ElMessage.info('正在翻译报告内容...')
  const isChinese = /[\u4e00-\u9fa5]/.test(content.value.substring(0, 200))
  const prompt = isChinese
    ? `请将以下中文报告翻译为英文，保持Markdown格式和引用角标不变：\n\n${content.value}`
    : `请将以下英文报告翻译为中文，保持Markdown格式和引用角标不变：\n\n${content.value}`
  handleAiChat(prompt)
}

function handleAutoFormat() {
  if (!content.value) return
  let formatted = content.value
  formatted = formatted.replace(/\n{3,}/g, '\n\n')
  formatted = formatted.replace(/^##\s*/gm, '## ')
  formatted = formatted.replace(/^###\s*/gm, '### ')
  formatted = formatted.replace(/^(?!#)\s{2,}/gm, '')
  formatted = formatted.trim() + '\n'
  content.value = formatted
  ElMessage.success('排版完成')
}

async function handleAiChat(userInput?: string) {
  const input = userInput || aiChatInput.value.trim()
  if (!input) return
  aiChatMessages.value.push({ role: 'user', content: input })
  if (!userInput) aiChatInput.value = ''
  aiChatLoading.value = true
  try {
    const token = localStorage.getItem('token')
    const res = await fetch('/api/v1/reports/ai-chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({
        messages: aiChatMessages.value,
        context: content.value.substring(0, 3000)
      })
    })
    if (res.ok) {
      const data = await res.json()
      const reply = data?.data?.content || data?.data || 'AI 暂时无法回复，请稍后再试'
      aiChatMessages.value.push({ role: 'assistant', content: reply })
    } else {
      aiChatMessages.value.push({ role: 'assistant', content: 'AI 助手暂时不可用，请检查后端服务是否正常。' })
    }
  } catch (e) {
    aiChatMessages.value.push({ role: 'assistant', content: '网络请求失败，请检查后端服务。' })
  } finally {
    aiChatLoading.value = false
  }
}

function insertAiResult(text: string) {
  content.value += '\n\n' + text
  showAiChat.value = false
  ElMessage.success('内容已插入')
}

async function copyContent() {
  try {
    await navigator.clipboard.writeText(content.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    // Fallback for browsers without clipboard API
    const ta = document.createElement('textarea')
    ta.value = content.value
    document.body.appendChild(ta)
    ta.select()
    try {
      document.execCommand('copy')
      ElMessage.success('已复制到剪贴板')
    } catch {
      ElMessage.error('复制失败，请手动选择文本')
    } finally {
      document.body.removeChild(ta)
    }
  }
}

function safeFilename(raw: string, fallback: string): string {
  if (!raw) return fallback
  const cleaned = raw.replace(/[\\/:*?"<>|\x00-\x1f]/g, '').trim()
  return cleaned.slice(0, 80) || fallback
}

async function handleExport(kind: 'docx' | 'pdf') {
  if (!currentReportId.value || !content.value) return
  exporting.value = true
  try {
    if (kind === 'docx') {
      await exportDocx()
    } else {
      await exportPdf()
    }
  } catch (e: any) {
    console.error(`导出 ${kind} 失败:`, e)
    ElMessage.error(e?.message || '导出失败，请重试')
  } finally {
    exporting.value = false
  }
}

/**
 * Word 导出：后端 /export/docx 产出 .docx，走 fetch + Authorization header，
 * 再把 Blob 塞进临时 <a download> 触发保存。EventSource/window.open 无法带 JWT。
 */
async function exportDocx() {
  const token = userStore.token || localStorage.getItem('token') || ''
  const resp = await fetch(`/api/v1/reports/${currentReportId.value}/export/docx`, {
    method: 'GET',
    headers: { Authorization: `Bearer ${token}` }
  })
  if (!resp.ok) throw new Error(`导出接口返回 ${resp.status}`)
  const blob = await resp.blob()
  const filename = safeFilename(form.value.title, 'report') + '.docx'
  triggerBlobDownload(blob, filename)
  ElMessage.success('Word 已导出')
}

/**
 * PDF 导出：html2pdf 从预览 DOM 抓图转 PDF，保留 [n] 角标样式。
 * 若当前不是预览态，先切过去并等 DOM 更新，否则抓到的是空节点。
 */
async function exportPdf() {
  if (viewMode.value !== 'preview') {
    viewMode.value = 'preview'
    await nextTick()
  }
  const el = previewEl.value
  if (!el) throw new Error('预览内容未就绪')
  // 动态 import 避免首屏 bundle 体积被 html2pdf 拖胖（jsPDF + html2canvas）
  const html2pdf = (await import('html2pdf.js')).default
  const filename = safeFilename(form.value.title, 'report') + '.pdf'
  await html2pdf()
    .set({
      filename,
      margin: [15, 15, 20, 15], // mm: 上 右 下 左
      html2canvas: { scale: 2, useCORS: true },
      jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
      // pagebreak 不在 html2pdf.js 的 .d.ts 里但运行期支持；用 any 绕过类型
      ...({ pagebreak: { mode: ['css', 'legacy'] } } as any)
    })
    .from(el)
    .save()
  ElMessage.success('PDF 已导出')
}

function triggerBlobDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  // 稍延迟释放，避免部分浏览器触发下载前就被回收
  setTimeout(() => URL.revokeObjectURL(url), 2000)
}
</script>

<style scoped>
.workspace-container {
  display: flex;
  gap: 20px;
  height: calc(100vh - 140px);
}
.config-panel {
  width: 360px;
  flex-shrink: 0;
}
.config-card {
  height: 100%;
  overflow: auto;
}
.editor-panel {
  flex: 1;
  min-width: 0;
}
.editor-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.editor-card :deep(.el-card__body) {
  flex: 1;
  overflow: auto;
}
.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #0f172a;
}
.editor-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 400px;
}
.generating-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  color: #6366f1;
  font-size: 14px;
}
.report-editor {
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 14px;
  line-height: 1.8;
}
.report-editor :deep(textarea) {
  min-height: 600px;
}

/* Markdown 预览区 —— 保持与编辑器一致的行高/字号 */
.report-preview,
.streaming-editor {
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 14px;
  line-height: 1.85;
  color: #0f172a;
  padding: 4px 8px 24px;
  min-height: 600px;
  overflow-wrap: break-word;
}
.streaming-editor {
  overflow-y: auto;
}

/* 打字光标闪烁动画 */
.streaming-editor :deep(.typing-cursor) {
  display: inline;
  color: #6366f1;
  font-weight: 400;
  animation: cursor-blink 0.8s step-end infinite;
}
@keyframes cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.report-preview :deep(h1),
.report-preview :deep(h2),
.report-preview :deep(h3),
.streaming-editor :deep(h1),
.streaming-editor :deep(h2),
.streaming-editor :deep(h3) {
  font-weight: 600;
  color: #0f172a;
  margin: 1.2em 0 0.6em;
}
.report-preview :deep(p),
.streaming-editor :deep(p) { margin: 0.6em 0; }
.report-preview :deep(ul),
.report-preview :deep(ol),
.streaming-editor :deep(ul),
.streaming-editor :deep(ol) { margin: 0.5em 0; padding-left: 1.4em; }
.report-preview :deep(code),
.streaming-editor :deep(code) {
  background: #f4f6fa;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 0.92em;
}
.report-preview :deep(sup.cite),
.streaming-editor :deep(sup.cite) {
  display: inline-block;
  margin: 0 2px;
  padding: 0 4px;
  border-radius: 8px;
  background: #ede9fe;
  color: #6366f1;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s, color 0.15s;
}
.report-preview :deep(sup.cite:hover),
.streaming-editor :deep(sup.cite:hover) {
  background: #6366f1;
  color: #fff;
}

/* 右侧引用溯源面板 */
.citations-panel {
  width: 340px;
  flex-shrink: 0;
}
.citations-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.citations-card :deep(.el-card__body) {
  flex: 1;
  overflow: auto;
  padding: 0;
  display: flex;
  flex-direction: column;
}
.right-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.right-tabs :deep(.el-tabs__header) {
  margin: 0 12px;
}
.right-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: auto;
  padding: 12px;
}
.right-tabs :deep(.el-tab-pane) {
  height: 100%;
}
.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}
.empty-tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 300px;
  color: #64748b;
  font-size: 13px;
}
.quality-result {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.quality-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  background: linear-gradient(135deg, #eef2ff 0%, #f5f3ff 100%);
  border: 1px solid #c7d2fe;
  border-radius: 10px;
  padding: 12px 14px;
}
.quality-score-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 64px;
}
.quality-score {
  font-size: 28px;
  font-weight: 700;
  color: #4338ca;
  line-height: 1;
}
.quality-score-label {
  margin-top: 4px;
  font-size: 11px;
  color: #6366f1;
}
.quality-summary {
  flex: 1;
  font-size: 13px;
  line-height: 1.6;
  color: #334155;
}
.quality-dims {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.quality-dim {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  padding: 10px 12px;
}
.quality-dim-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  font-size: 13px;
}
.quality-dim-title {
  font-weight: 600;
  color: #1e293b;
}
.quality-dim-score {
  color: #6366f1;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.quality-list {
  margin: 0;
  padding-left: 16px;
  font-size: 12.5px;
  line-height: 1.7;
  color: #475569;
}
.quality-list li {
  margin-bottom: 8px;
}
.quality-empty {
  font-size: 12px;
  color: #10b981;
}
.issue-sentence {
  color: #1e293b;
  margin-left: 4px;
}
.issue-reason {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}
.cite-badge {
  display: inline-block;
  background: #eef2ff;
  color: #6366f1;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  margin-right: 4px;
}
.quality-actions {
  display: flex;
  justify-content: flex-end;
}
.citations-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.citation-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
  transition: all 0.3s;
}
.citation-card.highlighted {
  background: #ede9fe;
  border-color: #6366f1;
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.2);
}
.citation-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 12px;
}
.citation-idx {
  color: #6366f1;
  font-weight: 700;
  font-size: 14px;
}
.citation-icon {
  font-size: 15px;
  line-height: 1;
  flex-shrink: 0;
}
.citation-file {
  flex: 1;
  color: #475569;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.citation-body {
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
  max-height: 8.5em;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
}
.citation-foot {
  margin-top: 8px;
}
.citation-score {
  display: flex;
  align-items: center;
  gap: 8px;
}
.score-bar {
  flex: 1;
  height: 4px;
  background: #e2e8f0;
  border-radius: 2px;
  overflow: hidden;
}
.score-fill {
  height: 100%;
  background: linear-gradient(90deg, #6366f1, #8b5cf6);
  border-radius: 2px;
  transition: width 0.3s;
}
.score-text {
  font-size: 11px;
  color: #64748b;
  white-space: nowrap;
}

/* MCP 引入相关 */
.mcp-imported-hint {
  font-size: 12px;
  color: #10b981;
  margin-top: 4px;
}
.external-data-btns {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.external-data-btns .el-button {
  flex: 1;
}
.template-option {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.template-option-name {
  font-size: 14px;
  font-weight: 500;
  color: #0f172a;
}
.template-option-desc {
  font-size: 12px;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 240px;
}
.template-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  padding: 6px 10px;
  background: #f5f3ff;
  border-radius: 6px;
  border-left: 3px solid #6366f1;
  font-size: 12px;
  color: #475569;
  line-height: 1.5;
}
.web-url {
  font-size: 11px;
  color: #6366f1;
  text-decoration: none;
  word-break: break-all;
}
.web-url:hover {
  text-decoration: underline;
}
.editor-wrapper {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.editor-wrapper .report-editor {
  flex: 1;
}
.ai-assist-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f8fafc;
  border-top: 1px solid #e2e8f0;
  border-radius: 0 0 8px 8px;
}
.ai-assist-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #94a3b8;
}
.ai-assist-hint kbd {
  padding: 1px 5px;
  font-size: 11px;
  background: #e2e8f0;
  border-radius: 3px;
  border: 1px solid #cbd5e1;
  font-family: inherit;
}
.ai-assist-actions {
  display: flex;
  gap: 6px;
}
.ai-chat-body {
  display: flex;
  flex-direction: column;
  height: 420px;
}
.ai-chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
  margin-bottom: 12px;
}
.ai-chat-welcome {
  text-align: center;
  padding: 30px 20px;
  color: #64748b;
}
.ai-chat-welcome p {
  margin: 12px 0;
  font-size: 14px;
}
.ai-chat-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
}
.ai-suggestion-tag {
  cursor: pointer;
  transition: all 0.15s;
}
.ai-suggestion-tag:hover {
  background: #ede9fe;
  color: #6366f1;
  border-color: #6366f1;
}
.ai-chat-msg {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 12px;
  padding: 8px 10px;
  border-radius: 8px;
}
.ai-chat-msg.user {
  background: #ede9fe;
}
.ai-chat-msg.assistant {
  background: #fff;
  border: 1px solid #e2e8f0;
}
.msg-avatar {
  font-size: 18px;
  flex-shrink: 0;
  width: 28px;
  text-align: center;
}
.msg-content {
  flex: 1;
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
  white-space: pre-wrap;
}
.ai-chat-input-bar {
  flex-shrink: 0;
}
.fetch-result {
  margin-top: 16px;
}
.fetch-content {
  margin-top: 8px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: #0f172a;
  max-height: 300px;
  overflow-y: auto;
}
.mcp-dialog-body {
  min-height: 300px;
}
.mcp-results {
  margin-top: 16px;
}
.mcp-results-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 13px;
  color: #475569;
}
.mcp-results-list {
  max-height: 400px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.mcp-article-item {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.mcp-article-item:hover {
  border-color: #6366f1;
}
.mcp-article-item.selected {
  border-color: #6366f1;
  background: #ede9fe;
}
.mcp-article-title {
  font-size: 14px;
  font-weight: 500;
  color: #0f172a;
  margin-bottom: 4px;
}
.mcp-article-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 4px;
}
.mcp-article-summary {
  font-size: 12px;
  color: #475569;
  line-height: 1.6;
}

/* AI 生成进度条 */
.progress-bar {
  margin-bottom: 12px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
  border-radius: 8px;
  border: 1px solid #ddd6fe;
}
.progress-steps {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.progress-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex: 1;
}
.step-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  background: #e2e8f0;
  color: #94a3b8;
  transition: all 0.3s;
}
.progress-step.done .step-dot {
  background: #10b981;
  color: #fff;
}
.progress-step.active .step-dot {
  background: #6366f1;
  color: #fff;
  box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.2);
}
.step-label {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
}
.progress-step.active .step-label {
  color: #6366f1;
  font-weight: 600;
}
.progress-step.done .step-label {
  color: #10b981;
}

/* 段落级改写 */
.section-editable :deep(.section-block) {
  position: relative;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.15s;
}
.section-editable :deep(.section-block:hover) {
  background: rgba(99, 102, 241, 0.04);
}
.section-editable :deep(.section-block:hover::after) {
  content: '';
  position: absolute;
  top: 4px;
  right: 4px;
  display: flex;
  gap: 4px;
}
.section-editable :deep(.section-actions) {
  display: none;
  position: absolute;
  top: 4px;
  right: 8px;
  gap: 4px;
  z-index: 10;
}
.section-editable :deep(.section-block:hover .section-actions) {
  display: flex;
}
.section-action-btn {
  padding: 2px 8px;
  font-size: 11px;
  border-radius: 4px;
  border: 1px solid #ddd6fe;
  background: #ede9fe;
  color: #6366f1;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.section-action-btn:hover {
  background: #6366f1;
  color: #fff;
  border-color: #6366f1;
}

.search-conditions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.condition-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.condition-label {
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
  min-width: 70px;
  padding-top: 4px;
}
.condition-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.depth-group {
  width: 100%;
}
.depth-group :deep(.el-radio-button__inner) {
  width: 100%;
  padding: 8px 10px;
}
.depth-group :deep(.el-radio-button) {
  flex: 1;
}
.depth-hint-inline {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: #94a3b8;
  font-weight: normal;
}
.depth-footnote {
  margin-top: 4px;
  font-size: 11px;
  color: #94a3b8;
}

/* ---- 引用 hover 预览浮层（Granola 式） ---- */
.cite-popover {
  position: absolute;
  z-index: 9999;
  width: 360px;
  max-height: 280px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  box-shadow: 0 12px 32px -8px rgba(15, 23, 42, 0.2), 0 2px 6px rgba(15, 23, 42, 0.08);
  padding: 12px 14px;
  font-size: 13px;
  line-height: 1.65;
  color: #1e293b;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.cite-pop-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #475569;
}
.cite-pop-idx {
  color: #6366f1;
  font-weight: 700;
  font-size: 13px;
}
.cite-pop-icon {
  font-size: 14px;
}
.cite-pop-file {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 500;
  color: #334155;
}
.cite-pop-body {
  flex: 1;
  overflow: auto;
  font-size: 12.5px;
  color: #334155;
  background: #f8fafc;
  border-radius: 6px;
  padding: 8px 10px;
  max-height: 180px;
  line-height: 1.7;
}
.cite-pop-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: #94a3b8;
}
.cite-pop-score {
  color: #6366f1;
  font-weight: 600;
}
.cite-pop-hint {
  font-style: italic;
}

/* 进场 / 退场小动画 */
.cite-pop-enter-active,
.cite-pop-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.cite-pop-enter-from,
.cite-pop-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
