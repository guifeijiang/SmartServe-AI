export const STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 'NEW', label: '新建' },
  { value: 'ASSIGNED', label: '已分配' },
  { value: 'IN_PROGRESS', label: '处理中' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'CLOSED', label: '已关闭' },
]

export const PRIORITY_OPTIONS = [
  { value: '', label: '全部优先级' },
  { value: 'LOW', label: '低' },
  { value: 'MEDIUM', label: '中' },
  { value: 'HIGH', label: '高' },
  { value: 'URGENT', label: '紧急' },
]

export const CATEGORY_OPTIONS = [
  { value: 'HARDWARE', label: '硬件' },
  { value: 'SOFTWARE', label: '软件' },
  { value: 'BILLING', label: '账单' },
  { value: 'ACCOUNT', label: '账号' },
  { value: 'OTHER', label: '其他' },
]

const STATUS_TEXT = Object.fromEntries(STATUS_OPTIONS.filter((item) => item.value).map((item) => [item.value, item.label]))
const PRIORITY_TEXT = Object.fromEntries(PRIORITY_OPTIONS.filter((item) => item.value).map((item) => [item.value, item.label]))
const CATEGORY_TEXT = Object.fromEntries(CATEGORY_OPTIONS.map((item) => [item.value, item.label]))

const OPERATION_TEXT = {
  CREATE: '创建工单',
  ASSIGN: '分配客服',
  CLAIM: '客服接单',
  HANDLE: '处理跟进',
  RESOLVE: '标记解决',
  CLOSE: '关闭工单',
}

export function statusLabel(value) {
  return STATUS_TEXT[value] || value || '-'
}

export function priorityLabel(value) {
  return PRIORITY_TEXT[value] || value || '-'
}

export function categoryLabel(value) {
  return CATEGORY_TEXT[value] || value || '-'
}

export function operationLabel(value) {
  return OPERATION_TEXT[value] || value || '-'
}

export function formatTime(value) {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ')
}

export function roleHomeTitle(role) {
  if (role === 'ADMIN') {
    return '管理员工作台'
  }
  if (role === 'AGENT') {
    return '客服工作台'
  }
  return '客户工作台'
}
