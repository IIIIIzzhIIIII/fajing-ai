<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="back()"><fj-icon name="arrow-left" :size="16" /></view>
      <view class="topbar-title">用户协议</view>
    </view>

    <scroll-view scroll-y class="scroll">
      <view class="pad doc">
        <view class="doc-h">发镜 AI 用户协议</view>
        <view class="doc-time">更新日期：2026 年 11 月</view>

        <view class="doc-sec">
          <view class="doc-t">1. 服务内容</view>
          <view class="doc-p">发镜 AI 提供基于人工智能的发型预览、付费高清导出、理发师沟通卡与发型讨论服务。生成结果为算法模拟效果，实际剪发效果受发质、发量、理发师操作等影响，仅供决策参考。</view>
        </view>

        <view class="doc-sec">
          <view class="doc-t">2. 账号与使用规范</view>
          <view class="doc-p">你应使用本人照片进行预览，不得上传他人照片或侵犯他人肖像权的内容；不得利用本服务制作、传播违法违规信息。讨论区内容需经内容安全校验，违规内容将被删除，严重者限制账号使用。</view>
        </view>

        <view class="doc-sec">
          <view class="doc-t">3. 付费与退款</view>
          <view class="doc-p">高清无水印导出为增值服务，按页面展示价格付费。因服务自身故障导致未获取到效果图的，可申请退还相应费用；已成功导出高清图的订单不支持退款。</view>
        </view>

        <view class="doc-sec">
          <view class="doc-t">4. 知识产权</view>
          <view class="doc-p">你上传的照片归你所有；生成结果图你可用于个人用途，不得用于商业宣传或冒充真人实拍。发型库图片版权归发镜 AI 所有。</view>
        </view>

        <view class="doc-sec">
          <view class="doc-t">5. AI 生成内容提示</view>
          <view class="doc-p">所有 AI 生成图片均带有「AI 生成」标识，分享或转发时请保留标识，不得用于误导他人。</view>
        </view>

        <view class="doc-sec">
          <view class="doc-t">6. 协议变更</view>
          <view class="doc-p">我们可能根据需要更新本协议，更新后会在应用内提示，继续使用即视为同意更新后的协议。</view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { goBack } from '@/utils/nav'

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
</style>
