import { http, type PageResult } from '@/api/http'

export type StatisticsPlanStatus = 'PUBLISHED' | 'IN_PROGRESS' | 'FINISHED' | 'CANCELLED'
export type StudyStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED'
export type StatisticsExamStatus =
  'NOT_REQUIRED' | 'NOT_STARTED' | 'IN_PROGRESS' | 'PASSED' | 'FAILED'
export type CompletionStatus = 'NOT_COMPLETED' | 'COMPLETED'

export interface StatisticsOverview {
  planCount: number
  participantCount: number
  startedCount: number
  studyCompletedCount: number
  examPassedCount: number
  completedCount: number
  completionRate: number
  requiredDurationMillis: number
  effectiveDurationMillis: number
  averageEffectiveDurationMillis: number
}

export interface PlanStatistics {
  planId: string
  planName: string
  status: StatisticsPlanStatus
  startAt: string
  endAt: string
  examRequired: boolean
  participantCount: number
  startedCount: number
  studyCompletedCount: number
  examPassedCount: number
  completedCount: number
  completionRate: number
}

export interface ParticipantStatistics {
  taskId: string
  planId: string
  planName: string
  userId: string
  orgId?: string
  orgName?: string
  username: string
  displayName: string
  studyStatus: StudyStatus
  examStatus: StatisticsExamStatus
  completionStatus: CompletionStatus
  examScore?: number
  examPassed?: boolean
  requiredDurationMillis: number
  effectiveDurationMillis: number
  completedAt?: string
}

export interface PlanStatisticsQuery {
  pageNumber: number
  pageSize: number
  keyword?: string
  status?: StatisticsPlanStatus | ''
}

export interface ParticipantStatisticsQuery {
  pageNumber: number
  pageSize: number
  planId?: string
  keyword?: string
  completionStatus?: CompletionStatus | ''
}

/** 查询企业全部计划或指定计划的培训统计总览。 */
export function getStatisticsOverview(planId?: string) {
  return http.get<StatisticsOverview>('/training/statistics/overview', {
    params: { planId: planId || undefined },
  })
}

/** 分页查询培训计划完成情况。 */
export function getPlanStatistics(params: PlanStatisticsQuery) {
  return http.get<PageResult<PlanStatistics>>('/training/statistics/plans', { params })
}

/** 分页查询学员培训、考试和有效学时明细。 */
export function getParticipantStatistics(params: ParticipantStatisticsQuery) {
  return http.get<PageResult<ParticipantStatistics>>('/training/statistics/participants', {
    params,
  })
}
