/* eslint-env node */
module.exports = {
  root: true,
  env: {
    browser: true,
    node: true,
    es2022: true,
    'vue/setup-compiler-macros': true
  },
  extends: ['plugin:vue/vue3-essential', '@vue/eslint-config-prettier'],
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module'
  },
  globals: {
    uni: 'readonly',
    wx: 'readonly',
    plus: 'readonly',
    getApp: 'readonly',
    getCurrentPages: 'readonly'
  },
  ignorePatterns: ['dist/', 'node_modules/', 'unpackage/'],
  rules: {
    'no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    'no-undef': 'error',
    'vue/multi-word-component-names': 'off',
    // 暂不强制代码风格，只拦真实错误
    'prettier/prettier': 'off'
  }
}
