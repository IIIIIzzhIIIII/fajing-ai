<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, CopyDocument, Upload } from '@element-plus/icons-vue'
import { fetchPayConfig, savePayConfig, uploadPayCert, verifyPayChannel } from '@/api'

const loading = ref(false)
const saving = ref(false)
const verifying = reactive({ wx: false, alipay: false })
const verifyResult = reactive({ wx: null, alipay: null })
const ready = reactive({ wx: false, wxVirtual: false, douyin: false, xhs: false, alipay: false })
const secretConfigured = ref(true)
const suggested = reactive({
  wxNotifyUrl: '',
  douyinNotifyUrl: '',
  xhsNotifyUrl: '',
  alipayNotifyUrl: '',
  alipayReturnUrl: ''
})
const uploading = ref(false)

/** 掩码原值：聚焦清空、失焦为空时恢复，避免误覆盖 */
const maskOriginal = reactive({
  wxApiV3KeyEnc: '',
  wxVirtualAppkeyEnc: '',
  douyinSecretEnc: '',
  douyinSaltEnc: '',
  douyinTokenEnc: '',
  xhsSecretEnc: '',
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
  wxVirtualOfferId: '',
  wxVirtualEnv: 0,
  wxVirtualProductOne: '',
  wxVirtualProductAll: '',
  douyinEnabled: 0,
  douyinAppId: '',
  douyinSecretEnc: '',
  douyinSaltEnc: '',
  douyinTokenEnc: '',
  douyinNotifyUrl: '',
  douyinSandbox: 0,
  xhsEnabled: 0,
  xhsAppId: '',
  xhsSecretEnc: '',
  xhsMerchantId: '',
  xhsNotifyUrl: '',
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
  // 虚拟商品只用小程序虚拟支付，历史数据统一收敛
  if (form.wxPayMode !== 'virtual') {
    form.wxPayMode = 'virtual'
  }
  ;[
    'wxApiV3KeyEnc',
    'wxVirtualAppkeyEnc',
    'douyinSecretEnc',
    'douyinSaltEnc',
    'douyinTokenEnc',
    'xhsSecretEnc',
    'alipayPrivateKeyEnc',
    'alipayPublicKeyEnc'
  ].forEach((key) => {
    maskOriginal[key] = form[key] || ''
  })
}

function load() {
  loading.value = true
  fetchPayConfig()
    .then((data) => {
      applyConfig(data.config)
      ready.wx = !!data.wxReady
      ready.wxVirtual = !!data.wxVirtualReady
      ready.douyin = !!data.douyinReady
      ready.xhs = !!data.xhsReady
      ready.alipay = !!data.alipayReady
      secretConfigured.value = !!data.secretConfigured
      suggested.wxNotifyUrl = data.wxNotifyUrlSuggested || ''
      suggested.douyinNotifyUrl = data.douyinNotifyUrlSuggested || ''
      suggested.xhsNotifyUrl = data.xhsNotifyUrlSuggested || ''
      suggested.alipayNotifyUrl = data.alipayNotifyUrlSuggested || ''
      suggested.alipayReturnUrl = data.alipayReturnUrlSuggested || ''
      if (!form.douyinNotifyUrl) form.douyinNotifyUrl = suggested.douyinNotifyUrl
      if (!form.xhsNotifyUrl) form.xhsNotifyUrl = suggested.xhsNotifyUrl
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
            <span>微信小程序支付</span>
            <el-tag :type="ready.wx || ready.wxVirtual ? 'success' : 'info'" size="small">
              {{ ready.wx || ready.wxVirtual ? '参数完整' : '参数未完整' }}
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
            </el-radio-group>
            <div class="field-tip">虚拟商品必须选「小程序虚拟支付」，否则审核驳回。</div>
          </el-form-item>
          <el-form-item label="微信小程序AppID">
            <el-input v-model="form.wxAppid" placeholder="微信小程序 AppID" />
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
              <el-input
                v-model="form.wxNotifyUrl"
                placeholder="${PUBLIC_HTTPS_DOMAIN}/api/pay/wechat/miniprogram/notify"
              />
              <el-button :icon="CopyDocument" @click="copy(form.wxNotifyUrl)">复制</el-button>
            </div>
            <div class="field-tip">上线前替换为公网 HTTPS 域名，并在商户平台配置。</div>
          </el-form-item>
          <el-form-item label="虚拟支付 AppKey">
            <el-input
              v-model="form.wxVirtualAppkeyEnc"
              type="password"
              show-password
              placeholder="虚拟支付基础配置中的 AppKey"
              @focus="onFocusMask('wxVirtualAppkeyEnc')"
              @blur="onBlurMask('wxVirtualAppkeyEnc')"
            />
          </el-form-item>
          <el-form-item label="OfferID">
            <el-input v-model="form.wxVirtualOfferId" placeholder="虚拟支付基础配置中的 OfferID" />
          </el-form-item>
          <el-form-item label="支付环境">
            <el-radio-group v-model="form.wxVirtualEnv">
              <el-radio :value="0">现网环境</el-radio>
              <el-radio :value="1">沙箱环境</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="单张道具 ID">
            <el-input v-model="form.wxVirtualProductOne" placeholder="单价 6 元的道具 productId" />
          </el-form-item>
          <el-form-item label="全部道具 ID">
            <el-input v-model="form.wxVirtualProductAll" placeholder="单价 3 元的道具 productId（按数量购买）" />
            <div class="field-tip">
              首单 5 折用该道具 × 款数；优惠用完后改用上方 6 元道具 × 款数，两个都要配置。
            </div>
            <div class="field-tip">
              虚拟支付查单依赖服务端环境变量 WECHAT_APPID / WECHAT_SECRET；用户 session_key 过期时需重新进入小程序。
            </div>
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
            <span>支付宝</span>
            <el-tag :type="ready.alipay ? 'success' : 'info'" size="small">
              {{ ready.alipay ? '参数完整' : '参数未完整' }}
            </el-tag>
          </div>
        </template>

        <div class="card-tip">本配置仅用于 H5 网页支付和支付宝小程序，微信 / 抖音 / 小红书小程序请勿引用此配置。</div>

        <el-form label-width="120px">
          <el-form-item label="启用支付宝">
            <el-switch v-model="form.alipayEnabled" :active-value="1" :inactive-value="0" />
            <div class="field-tip">
              支付宝与微信支付按实际接入的小程序平台展示；抖音 / 小红书走各自平台收银台。
            </div>
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
              <el-input
                v-model="form.alipayNotifyUrl"
                placeholder="${PUBLIC_HTTPS_DOMAIN}/api/pay/alipay/notify"
              />
              <el-button :icon="CopyDocument" @click="copy(form.alipayNotifyUrl)">复制</el-button>
            </div>
            <div class="field-tip">上线前替换为公网 HTTPS 域名。</div>
            <div class="field-tip">必填：付款结果只以该地址的异步通知为准，缺失时用户付款后不会自动解锁。</div>
          </el-form-item>
          <el-form-item label="同步回跳地址">
            <div class="cert-field">
              <el-input
                v-model="form.alipayReturnUrl"
                placeholder="${H5_DOMAIN}/#/pages/result/result"
              />
              <el-button :icon="CopyDocument" @click="copy(form.alipayReturnUrl)">复制</el-button>
            </div>
            <div class="field-tip">
              必填：用户付款后跳回该地址确认结果；留空会停留在支付宝页面，App 内不会自动解锁。
            </div>
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

      <el-card shadow="never" class="pay-card">
        <template #header>
          <div class="card-head">
            <span>抖音小程序支付</span>
            <el-tag :type="ready.douyin ? 'success' : 'info'" size="small">
              {{ ready.douyin ? '参数完整' : '参数未完整' }}
            </el-tag>
          </div>
        </template>

        <el-form label-width="120px">
          <el-form-item label="启用抖音支付">
            <el-switch v-model="form.douyinEnabled" :active-value="1" :inactive-value="0" />
            <div class="field-tip">走抖音担保支付收银台，收银台内可选支付宝 / 微信 / 抖音支付。</div>
          </el-form-item>
          <el-form-item label="小程序 AppID">
            <el-input v-model="form.douyinAppId" placeholder="tt 开头的小程序 AppID" />
          </el-form-item>
          <el-form-item label="AppSecret">
            <el-input
              v-model="form.douyinSecretEnc"
              type="password"
              show-password
              placeholder="抖音开放平台 AppSecret"
              @focus="onFocusMask('douyinSecretEnc')"
              @blur="onBlurMask('douyinSecretEnc')"
            />
          </el-form-item>
          <el-form-item label="支付 SALT">
            <el-input
              v-model="form.douyinSaltEnc"
              type="password"
              show-password
              placeholder="担保支付设置中的 SALT"
              @focus="onFocusMask('douyinSaltEnc')"
              @blur="onBlurMask('douyinSaltEnc')"
            />
          </el-form-item>
          <el-form-item label="回调 Token">
            <el-input
              v-model="form.douyinTokenEnc"
              type="password"
              show-password
              placeholder="支付设置中的 Token（回调验签用）"
              @focus="onFocusMask('douyinTokenEnc')"
              @blur="onBlurMask('douyinTokenEnc')"
            />
          </el-form-item>
          <el-form-item label="回调地址">
            <div class="cert-field">
              <el-input v-model="form.douyinNotifyUrl" placeholder="https://api.example.com/api/order/notify/douyin" />
              <el-button :icon="CopyDocument" @click="copy(form.douyinNotifyUrl)">复制</el-button>
            </div>
            <div class="field-tip">必须为公网 HTTPS 地址，并在抖音开放平台支付设置中配置。</div>
          </el-form-item>
          <el-form-item label="沙箱环境">
            <el-switch v-model="form.douyinSandbox" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never" class="pay-card">
        <template #header>
          <div class="card-head">
            <span>小红书小程序支付</span>
            <el-tag :type="ready.xhs ? 'success' : 'info'" size="small">
              {{ ready.xhs ? '参数完整' : '待联调' }}
            </el-tag>
          </div>
        </template>

        <el-form label-width="120px">
          <el-form-item label="启用小红书支付">
            <el-switch v-model="form.xhsEnabled" :active-value="1" :inactive-value="0" />
            <div class="field-tip">需先在小红书开放平台开通交易能力；支付接口需按官方文档完成联调。</div>
          </el-form-item>
          <el-form-item label="小程序 AppID">
            <el-input v-model="form.xhsAppId" placeholder="小红书小程序 AppID" />
          </el-form-item>
          <el-form-item label="AppSecret">
            <el-input
              v-model="form.xhsSecretEnc"
              type="password"
              show-password
              placeholder="小红书开放平台 AppSecret"
              @focus="onFocusMask('xhsSecretEnc')"
              @blur="onBlurMask('xhsSecretEnc')"
            />
          </el-form-item>
          <el-form-item label="商户号">
            <el-input v-model="form.xhsMerchantId" placeholder="担保支付商户号" />
          </el-form-item>
          <el-form-item label="回调地址">
            <div class="cert-field">
              <el-input v-model="form.xhsNotifyUrl" placeholder="https://api.example.com/api/order/notify/xhs" />
              <el-button :icon="CopyDocument" @click="copy(form.xhsNotifyUrl)">复制</el-button>
            </div>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never" class="pay-card is-wide">
        <template #header>
          <div class="card-head">
            <span>小程序平台接入</span>
            <el-tag type="info" size="small">按平台展示支付</el-tag>
          </div>
        </template>

        <div class="reserve-list">
          <div class="reserve-row">
            <span class="reserve-name">微信小程序</span>
            <span class="reserve-desc">
              {{ form.wxPayMode === 'virtual' ? '虚拟支付，客户端拉起微信收银台' : '普通 JSAPI 支付' }}
            </span>
            <el-tag :type="ready.wx || ready.wxVirtual ? 'success' : 'info'" size="small">
              {{ ready.wx || ready.wxVirtual ? '参数完整' : '待配置' }}
            </el-tag>
          </div>
          <div class="reserve-row">
            <span class="reserve-name">抖音小程序</span>
            <span class="reserve-desc">
              担保支付收银台（tt.pay），收银台内可选支付宝 / 微信 / 抖音支付。
            </span>
            <el-tag :type="ready.douyin ? 'success' : 'info'" size="small">
              {{ ready.douyin ? '参数完整' : '待配置' }}
            </el-tag>
          </div>
          <div class="reserve-row">
            <span class="reserve-name">小红书小程序</span>
            <span class="reserve-desc">
              担保支付收银台，需先开通交易能力并完成官方接口联调。
            </span>
            <el-tag type="info" size="small">{{ ready.xhs ? '参数完整' : '待联调' }}</el-tag>
          </div>
        </div>
        <div class="field-tip">
          支付方式按当前实际接入的小程序平台展示；保存后可在用户端解锁弹层直接选择对应支付方式。
        </div>
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

  &.is-wide {
    grid-column: 1 / -1;
  }
}
.reserve-list {
  display: grid;
  gap: 10px;
  margin-bottom: 4px;
}
.reserve-row {
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 13px;
}
.reserve-name {
  flex: 0 0 100px;
  font-weight: 600;
  color: #3b3337;
}
.reserve-desc {
  flex: 1;
  color: #7d7278;
  line-height: 1.6;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
  color: #2b2628;
}
.card-tip {
  margin-bottom: 14px;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.6;
  color: #a0601a;
  background: #fff7ea;
  border-radius: 8px;
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
