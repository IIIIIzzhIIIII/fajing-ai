<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="back()"><fj-icon name="arrow-left" :size="16" /></view>
      <view class="topbar-title">隐私政策</view>
    </view>

    <scroll-view scroll-y class="scroll">
      <view class="pad doc">
        <view class="doc-h">发镜 AI 隐私政策</view>
        <view class="doc-time">更新日期：2026 年 11 月</view>

        <fj-doc-content v-if="site.privacyContent" :text="site.privacyContent" />
        <template v-else>

        <view class="doc-sec">
          <view class="doc-t">1. 我们收集哪些信息</view>
          <view class="doc-p">你上传的人脸照片（含面部特征）、你选择的发型与发色、生成结果图。上述信息仅用于提供换发型预览。</view>
        </view>

        <view class="doc-sec">
          <view class="doc-t">2. 人脸照片属于敏感个人信息</view>
          <view class="doc-p">人脸照片属于《个人信息保护法》规定的敏感个人信息。我们会在你<b>单独勾选同意</b>后才开始处理，且仅用于本次 AI 换发，不建立人脸特征库、不用于模型训练、不提供给第三方做其他用途。</view>
        </view>

        <view class="doc-sec">
          <view class="doc-t">3. 保存期限与删除</view>
          <view class="doc-p">上传的原图仅在生成期间使用，生成结束 24 小时内由服务端定时任务自动删除；生成结果图保存在你的试发记录中，你可随时在「我的 → 隐私与数据」中一键删除本机数据。</view>
        </view>

        <view class="doc-sec">
          <view class="doc-t">4. 委托处理</view>
          <view class="doc-p">生成换发效果时，照片会加密传输至 AI 图像处理服务商进行处理，服务商仅按我们的指令处理，不得留存或另作他用。</view>
        </view>

        <view class="doc-sec">
          <view class="doc-t">5. 你的权利</view>
          <view class="doc-p">你可以随时撤回同意、删除照片与记录、注销账号。撤回同意后，我们将停止处理并删除已上传的原图。</view>
        </view>

        <view class="doc-sec">
          <view class="doc-t">6. 未成年人</view>
          <view class="doc-p">未满 14 周岁的用户请在监护人陪同下使用，并由监护人代为同意本政策。</view>
        </view>

        <view class="doc-sec">
          <view class="doc-t">7. 联系我们</view>
          <view class="doc-p">如对个人信息处理有疑问，可通过「我的 → 联系客服」与我们联系。</view>
        </view>
        </template>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { goBack } from '@/utils/nav'
import { site } from '@/utils/site'
import FjDocContent from '@/components/FjDocContent.vue'

function back() {
  goBack()
}
</script>

<style lang="scss" scoped>
.doc {
  padding-bottom: 30px;
}
.doc-h {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.4px;
  margin: 6px 0 4px;
}
.doc-time {
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 18px;
}
.doc-sec {
  margin-bottom: 16px;
}
.doc-t {
  font-size: 13.5px;
  font-weight: 800;
  margin-bottom: 6px;
}
.doc-p {
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--ink-2);
}
.doc-p b {
  color: var(--pink-700);
}
</style>
