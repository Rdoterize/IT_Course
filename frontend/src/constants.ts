import type { AchievementType } from './types'
export const typeLabels: Record<string, string> = { TEACHING: '教学成果', RESEARCH: '科研项目', COMPETITION: '学科竞赛', SCHOLARSHIP: '奖学金', PATENT: '专利', PAPER: '学术论文', SOFTWARE: '软件著作权', CERTIFICATION: '行业认证' }
export const levelLabels: Record<string, string> = { INTERNATIONAL: '国际级', NATIONAL: '国家级', PROVINCIAL: '省部级', UNIVERSITY: '校级', OTHER: '其他' }
export const statusLabels: Record<string, string> = { DRAFT: '草稿', PENDING: '待审核', APPROVED: '已通过', REJECTED: '已退回' }
export const statusColors: Record<string, 'info' | 'warning' | 'success' | 'danger'> = { DRAFT: 'info', PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }
export const roleLabels: Record<string, string> = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }
export const participantLabels: Record<string, string> = { AUTHOR: '作者 / 负责人', MEMBER: '参与成员', ADVISOR: '指导教师' }
export const detailFields: Record<AchievementType, { key: string; label: string; placeholder?: string }[]> = {
  TEACHING: [{ key: 'projectNo', label: '项目 / 成果编号' }, { key: 'organization', label: '认定单位' }, { key: 'awardName', label: '获奖名称 / 等次' }],
  RESEARCH: [{ key: 'projectNo', label: '项目编号' }, { key: 'funding', label: '项目经费（万元）' }, { key: 'organization', label: '立项单位' }],
  COMPETITION: [{ key: 'competitionName', label: '竞赛名称' }, { key: 'awardName', label: '获奖等次' }, { key: 'organizer', label: '主办单位' }],
  SCHOLARSHIP: [{ key: 'scholarshipName', label: '奖学金名称' }, { key: 'amount', label: '金额（元）' }, { key: 'academicYear', label: '所属学年', placeholder: '例如：2025—2026' }],
  PATENT: [{ key: 'patentNo', label: '专利号 / 申请号' }, { key: 'patentType', label: '专利类型', placeholder: '发明 / 实用新型 / 外观设计' }, { key: 'patentStatus', label: '专利状态', placeholder: '申请 / 授权' }],
  PAPER: [{ key: 'journalName', label: '期刊 / 会议名称' }, { key: 'doi', label: 'DOI / 检索号' }, { key: 'paperStatus', label: '论文状态', placeholder: '录用 / 已发表' }],
  SOFTWARE: [{ key: 'registrationNo', label: '登记号' }, { key: 'version', label: '软件版本' }],
  CERTIFICATION: [{ key: 'certificateNo', label: '证书编号' }, { key: 'issuer', label: '发证机构' }, { key: 'validUntil', label: '有效期', placeholder: '长期有效或到期日期' }],
}
export const formatDate = (value?: string) => value ? value.slice(0, 19).replace('T', ' ') : '—'
export const formatSize = (size: number) => size < 1024 ? `${size} B` : size < 1048576 ? `${(size / 1024).toFixed(1)} KB` : `${(size / 1048576).toFixed(1)} MB`
export const localToday = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date())
