<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, CopyDocument, Upload } from '@element-plus/icons-vue'
import { fetchPayConfig, savePayConfig, uploadPayCert, verifyPayChannel } from '@/api'

const loading = ref(false)
const saving = ref(false)
const verifying = reactive({ wx: false, alipay: false })
const verifyResult = reactive({ wx: null, alipay: null })
const ready = reactive({ wx: false, alipay: false })
const secretConfigured = ref(true)
const suggested = reactive({ wxNotifyUrl: '', alipayNotifyUrl: '', alipayReturnUrl: '' })
const uploading = ref(false)

/** 掩码原值：聚焦清空、失焦为空时恢复，避免误覆盖 */
const maskOriginal = reactive({
  wxApiV3KeyEnc: '',
  wxVirtualAppkeyEnc: '',
  alipayPrivateKeyEnc: '',
  alipayPublicKeyEnc: ''
})

const form = reactive({
  wxEnabled: 0,
  wxPayMode: 'virtual',
  wxAppid: '',
  wxMchId: '',
  wxApiV3KeyEnc: '',
  wxCertSerialNo: '',
  wxPrivateKeyPath: '',
  wxNotifyUrl: '',
  wxVirtualAppkeyEnc: '',
  alipayEnabled: 0,
  alipayAppId: '',
  alipayPrivateKeyEnc: '',
  alipayPublicKeyEnc: '',
  alipayGateway: 'https://openapi.alipay.com/gateway.do',
  alipayNotifyUrl: '',
  alipayReturnUrl: ''
})

function applyConfig(config) {
  if (!config) return
  Object.keys(form).forEach((key) => {
    if (config[key] !== undefined && config[key] !== null) {
      form[key] = config[key]
    }
  })
  ;['wxApiV3KeyEnc', 'wxVirtualAppkeyEnc', 'alipayPrivateKeyEnc', 'alipayPublicKeyEnc'].forEach((key) => {
    maskOriginal[key] = form[key] || ''
  })
}

function load() {
  loading.value = true
  fetchPayConfig()
    .then((data) => {
      applyConfig(data.config)
      ready.wx = !!data.wxReady
      ready.alipay = !!data.alipayReady
      secretConfigured.value = !!data.secretConfigured
      suggested.wxNotifyUrl = data.wxNotifyUrlSuggested || ''
      suggested.alipayNotifyUrl = data.alipayNotifyUrlSuggested || ''
      suggested.alipayReturnUrl = data.alipayReturnUrlSuggested || ''
      if (!form.wxNotifyUrl) form.wxNotifyUrl = suggested.wxNotifyUrl
      if (!form.alipayNotifyUrl) form.alipayNotifyUrl = suggested.alipayNotifyUrl
      if (!form.alipayReturnUrl) form.alipayReturnUrl = suggested.alipayReturnUrl
    })
    .catch(() => {})
    .finally(() => {
      loading.value = false
    })
}

function onFocusMask(key) {
  if (String(form[key] || '').includes('*')) {
    form[key] = ''
  }
}

function onBlurMask(key) {
  if (!form[key]) {
    form[key] = maskOriginal[key]
  }
}

async function save() {
  saving.value = true
  try {
    await savePayConfig({ ...form })
    ElMessage.success('已保存，新配置对后续订单生效（已进行中的订单仍走旧参数）')
    load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function verify(channel) {
  verifying[channel] = true
  verifyResult[channel] = null
  try {
    const data = await verifyPayChannel(channel)
    verifyResult[channel] = {
      ok: !!data.ok,
      msg: data.msg || (data.ok ? '通过' : '失败')
    }
  } catch (e) {
    verifyResult[channel] = { ok: false, msg: '测试请求失败' }
  } finally {
    verifying[channel] = false
  }
}

async function handleCert(options) {
  const file = options && options.file
  if (!file) return
  uploading.value = true
  try {
    const data = await uploadPayCert(file)
    form.wxPrivateKeyPath = data.path || ''
    ElMessage.success('私钥已上传，保存配置后生效')
  } catch {
    // 拦截器已提示
  } finally {
    uploading.value = false
  }
}

function copy(text) {
  if (!text) return
  navigator.clipboard
    ?.writeText(text)
    .then(() => ElMessage.success('已复制'))
    .catch(() => ElMessage.warning('复制失败，请手动复制'))
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="pay-page">
    <el-alert
      v-if="!secretConfigured"
      type="error"
      :closable="false"
      show-icon
      title="未配置 PAY_CONFIG_SECRET，无法保存商户密钥"
      description="请先在服务器环境变量中配置至少 32 位的 PAY_CONFIG_SECRET，再回来填写密钥。"
      style="margin-bottom: 16px"
    />

    <div class="cards">
      <el-card shadow="never" class="pay-card">
        <template #header>
          <div class="card-head">
            <span>微信支付</span>
            <el-tag :type="ready.wx ? 'success' : 'info'" size="small">
              {{ ready.wx ? '参数完整' : '参数未完整' }}
            </el-tag>
          </div>
        </template>

        <el-form label-width="120px">
          <el-form-item label="启用微信支付">
            <el-switch v-model="form.wxEnabled" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item label="支付模式">
            <el-radio-group v-model="form.wxPayMode">
              <el-radio value="virtual">小程序虚拟支付</el-radio>
              <el-radio value="normal">普通 JSAPI</el-radio>
            </el-radio-group>
            <div class="field-tip">
              虚拟商品建议使用小程序虚拟支付；普通 JSAPI 售卖虚拟商品存在审核驳回风险。
              虚拟支付需先在微信侧开通并完成对接，当前仅保存开关。
            </div>
          </el-form-item>
          <el-form-item label="AppID">
            <el-input v-model="form.wxAppid" placeholder="小程序 AppID" />
          </el-form-item>
          <el-form-item label="商户号">
            <el-input v-model="form.wxMchId" placeholder="微信支付商户号 mchid" />
          </el-form-item>
          <el-form-item label="APIv3 密钥">
            <el-input
              v-model="form.wxApiV3KeyEnc"
              type="password"
              show-password
              placeholder="未设置"
              @focus="onFocusMask('wxApiV3KeyEnc')"
              @blur="onBlurMask('wxApiV3KeyEnc')"
            />
          </el-form-item>
          <el-form-item label="证书序列号">
            <el-input v-model="form.wxCertSerialNo" placeholder="商户 API 证书序列号" />
          </el-form-item>
          <el-form-item label="商户私钥">
            <div class="cert-field">
              <el-input v-model="form.wxPrivateKeyPath" readonly placeholder="上传 apiclient_key.pem 后自动填入路径" />
              <el-upload :show-file-list="false" :http-request="handleCert" accept=".pem">
                <el-button :icon="Upload" :loading="uploading">上传 .pem</el-button>
              </el-upload>
            </div>
          </el-form-item>
          <el-form-item label="回调地址">
            <div class="cert-field">
              <el-input v-model="form.wxNotifyUrl" placeholder="https://api.example.com/api/order/notify/wx" />
              <el-button :icon="CopyDocument" @click="copy(form.wxNotifyUrl)">复制</el-button>
            </div>
            <div class="field-tip">需为公网 HTTPS 地址，并在商户平台配置。</div>
          </el-form-item>
          <el-form-item label="虚拟支付 AppKey">
            <el-input
              v-model="form.wxVirtualAppkeyEnc"
              type="password"
              show-password
              placeholder="虚拟支付 AppKey（virtual 模式）"
              @focus="onFocusMask('wxVirtualAppkeyEnc')"
              @blur="onBlurMask('wxVirtualAppkeyEnc')"
            />
          </el-form-item>
          <el-form-item>
            <el-button :icon="Connection" :loading="verifying.wx" @click="verify('wx')">测试微信连接</el-button>
            <el-tag
              v-if="verifyResult.wx"
              :type="verifyResult.wx.ok ? 'success' : 'danger'"
              style="margin-left: 10px"
            >
              {{ verifyResult.wx.msg }}
            </el-tag>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never" class="pay-card">
        <template #header>
          <div class="card-head">
            <span>支付宝（仅 H5）</span>
            <el-tag :type="ready.alipay ? 'success' : 'info'" size="small">
              {{ ready.alipay ? '参数完整' : '参数未完整' }}
            </el-tag>
          </div>
        </template>

        <el-form label-width="120px">
          <el-form-item label="启用支付宝">
            <el-switch v-model="form.alipayEnabled" :active-value="1" :inactive-value="0" />
            <div class="field-tip">仅 H5 生效，小程序内不可用（平台规则限制）。</div>
          </el-form-item>
          <el-form-item label="应用 APPID">
            <el-input v-model="form.alipayAppId" placeholder="支付宝开放平台应用 APPID" />
          </el-form-item>
          <el-form-item label="应用私钥">
            <el-input
              v-model="form.alipayPrivateKeyEnc"
              type="textarea"
              :rows="4"
              placeholder="应用私钥（PKCS8）"
              @focus="onFocusMask('alipayPrivateKeyEnc')"
              @blur="onBlurMask('alipayPrivateKeyEnc')"
            />
          </el-form-item>
          <el-form-item label="支付宝公钥">
            <el-input
              v-model="form.alipayPublicKeyEnc"
              type="textarea"
              :rows="4"
              placeholder="支付宝公钥"
              @focus="onFocusMask('alipayPublicKeyEnc')"
              @blur="onBlurMask('alipayPublicKeyEnc')"
            />
          </el-form-item>
          <el-form-item label="网关">
            <el-input v-model="form.alipayGateway" placeholder="https://openapi.alipay.com/gateway.do" />
          </el-form-item>
          <el-form-item label="异步通知地址">
            <div class="cert-field">
              <el-input v-model="form.alipayNotifyUrl" placeholder="https://api.example.com/api/order/notify/alipay" />
              <el-button :icon="CopyDocument" @click="copy(form.alipayNotifyUrl)">复制</el-button>
            </div>
          </el-form-item>
          <el-form-item label="同步跳回地址">
            <el-input v-model="form.alipayReturnUrl" placeholder="https://example.com/#/pages/result/result" />
          </el-form-item>
          <el-form-item>
            <el-button :icon="Connection" :loading="verifying.alipay" @click="verify('alipay')">
              测试支付宝连接
            </el-button>
            <el-tag
              v-if="verifyResult.alipay"
              :type="verifyResult.alipay.ok ? 'success' : 'danger'"
              style="margin-left: 10px"
            >
              {{ verifyResult.alipay.msg }}
            </el-tag>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <div class="save-bar">
      <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
      <span class="save-tip">密钥将以 AES-256-GCM 加密存储，接口只返回掩码。</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pay-page {
  max-width: 1080px;
}
.cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.pay-card {
  border-radius: 12px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
  color: #2b2628;
}
.field-tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #a0949a;
}
.cert-field {
  display: flex;
  gap: 8px;
  width: 100%;
}
.save-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 20px;
}
.save-tip {
  font-size: 12px;
  color: #a0949a;
}
</style>
