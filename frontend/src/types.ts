export type UserRole = 'ADMIN' | 'TEACHER' | 'STUDENT'
export type AchievementType = 'TEACHING' | 'RESEARCH' | 'COMPETITION' | 'SCHOLARSHIP' | 'PATENT' | 'PAPER' | 'SOFTWARE' | 'CERTIFICATION'
export type AchievementStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED'
export type Level = 'INTERNATIONAL' | 'NATIONAL' | 'PROVINCIAL' | 'UNIVERSITY' | 'OTHER'
export interface User { id: number; username: string; name: string; role: UserRole; number: string; cohort: string; department: string; enabled: boolean }
export interface Participant { userId: number; name?: string; role: 'AUTHOR' | 'MEMBER' | 'ADVISOR'; sortOrder: number }
export interface Achievement {
  id: number; title: string; type: AchievementType; level: Level; achievedOn: string; description: string;
  details: Record<string, string>; status: AchievementStatus; ownerId: number; ownerName: string;
  createdAt: string; updatedAt: string; participants: Participant[]; indicatorIds: number[]
}
export interface AchievementInput { title: string; type: AchievementType; level: Level; achievedOn: string; description: string; details: Record<string, string>; participants: Participant[]; indicatorIds: number[] }
export interface Page<T> { items: T[]; total: number; page: number; size: number }
export interface Indicator { id: number; code: string; name: string; description: string }
export interface Attachment { id: number; originalName: string; contentType: string; size: number; createdAt: string }
export interface Review { id: number; reviewerName: string; approved: boolean; comment: string; createdAt: string }
export interface StatItem { name: string; value: number }
export interface Stats { total: number; participants: number; teachers: number; students: number; pending: number; byType: StatItem[]; byLevel: StatItem[]; byYear: StatItem[]; byMonth: StatItem[]; topContributors: StatItem[] }
