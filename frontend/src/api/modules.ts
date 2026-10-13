import { del, get, post, put } from './http'
import type { Exam, KnowledgePoint, PageResult, Paper, PracticeQuestion, Question, StudentStatistics, Subject, UserProfile } from '@/types'

export const authApi = {
  login: (payload: { username: string; password: string }) => post<{ userId: number; username: string; nickname: string; roles: string[]; accessToken: string; expiresIn: number }>('/auth/login', payload),
  register: (payload: { username: string; password: string; confirmPassword: string; nickname: string; email?: string; phone?: string }) => post<void>('/auth/register', payload),
  me: () => get<UserProfile>('/auth/me'),
  /** Revokes the presented access token server-side so it cannot be replayed. */
  logout: () => post<void>('/auth/logout'),
}

/**
 * `enabledOnly` defaults to true on the backend, which hides disabled rows.
 * Management screens must pass false, otherwise a disabled subject or knowledge
 * point disappears from the very list used to re-enable it.
 */
export const subjectApi = {
  list: (params: { enabledOnly?: boolean } = {}) =>
    get<Subject[]>('/subjects', params.enabledOnly === undefined ? undefined : params),
  create: (data: Partial<Subject>) => post<Subject>('/subjects', data),
  update: (id: number, data: Partial<Subject>) => put<Subject>(`/subjects/${id}`, data),
  remove: (id: number) => del<void>(`/subjects/${id}`),
}

export const knowledgeApi = {
  list: (subjectIdOrOptions?: number | { subjectId?: number; enabledOnly?: boolean }) => {
    const options = typeof subjectIdOrOptions === 'number' ? { subjectId: subjectIdOrOptions } : (subjectIdOrOptions || {})
    const params: Record<string, unknown> = {}
    if (options.subjectId) params.subjectId = options.subjectId
    if (options.enabledOnly !== undefined) params.enabledOnly = options.enabledOnly
    return get<KnowledgePoint[]>('/knowledge-points', Object.keys(params).length ? params : undefined)
  },
  create: (data: Partial<KnowledgePoint>) => post<KnowledgePoint>('/knowledge-points', data),
  update: (id: number, data: Partial<KnowledgePoint>) => put<KnowledgePoint>(`/knowledge-points/${id}`, data),
  remove: (id: number) => del<void>(`/knowledge-points/${id}`),
}

export const questionApi = {
  page: (params: Record<string, unknown> = {}) => get<PageResult<Question>>('/questions', { page: 1, size: 20, ...params }),
  detail: (id: number) => get<Question>(`/questions/${id}`),
  create: (data: Partial<Question>) => post<Question>('/questions', data),
  update: (id: number, data: Partial<Question>) => put<Question>(`/questions/${id}`, data),
  remove: (id: number) => del<void>(`/questions/${id}`),
  createBatch: (questions: Record<string, unknown>[]) => post<{ count: number; ids: number[] }>('/questions/batch', { questions }),
  removeBatch: (ids: number[]) => post<{ deleted: number }>('/questions/batch-delete', { ids }),
}

export const practiceApi = {
  count: (params: { subjectId?: number; knowledgePointId?: number; type?: string; difficulty?: string }) =>
    get<number>('/practice/count', { ...params }),
  start: (payload: { subjectId?: number; knowledgePointId?: number; type?: string; difficulty?: string; count: number; mode: string }) => {
    // Do not send empty strings from clearable selects; omitted filters mean "all".
    const normalized = {
      ...payload,
      type: payload.type?.trim() || undefined,
      difficulty: payload.difficulty?.trim() || undefined,
    }
    return post<PracticeQuestion[]>('/practice/start', normalized)
  },
  answer: (payload: { questionId: number; answerContent: unknown; mode: string }) => post<{ questionId: number; correct: boolean; message: string }>('/practice/answer', payload),
}

export const wrongApi = {
  list: () => get<Array<{ id: number; questionId: number; wrongCount: number; redoCount: number; masteryLevel: number; lastWrongAt?: string }>>('/wrong-questions'),
  markCorrect: (questionId: number) => post(`/wrong-questions/${questionId}/redo-correct`),
  markWrong: (questionId: number) => post(`/wrong-questions/${questionId}/mark-wrong`),
}

export const favoriteApi = {
  page: () => get<PageResult<{ id: number; questionId: number; content: string; type: string; difficulty: string; createdAt?: string }>>('/favorites', { page: 1, size: 100 }),
  add: (questionId: number) => post<void>(`/favorites/${questionId}`),
  remove: (questionId: number) => del<void>(`/favorites/${questionId}`),
  exists: (questionId: number) => get<{ favorite: boolean }>(`/favorites/${questionId}`),
}

export const noteApi = {
  list: (questionId: number) => get<Array<{ id: number; userId: number; questionId: number; content: string; createdAt?: string; updatedAt?: string }>>(`/question-notes/question/${questionId}`),
  create: (questionId: number, content: string) => post(`/question-notes/question/${questionId}`, { content }),
  update: (id: number, content: string) => put(`/question-notes/${id}`, { content }),
  remove: (id: number) => del<void>(`/question-notes/${id}`),
}

export interface PaperDetailResponse {
  paper: Paper
  questions: Array<{ id: number; questionId: number; questionNo: number; score: number; content: string; type: string; analysis?: string | null; difficulty?: string | null; answerConfig?: string | null }>
}

export const paperApi = {
  page: (params: Record<string, unknown> = {}) => get<PageResult<Paper>>('/papers', { page: 1, size: 100, ...params }),
  create: (payload: Record<string, unknown>) => post<PaperDetailResponse>('/papers', payload),
  update: (id: number, payload: Record<string, unknown>) => put<PaperDetailResponse>(`/papers/${id}`, payload),
  removeBatch: (ids: number[]) => post<{ deleted: number }>('/papers/batch-delete', { ids }),
  generate: (payload: Record<string, unknown>) => post<PaperDetailResponse>('/papers/generate', payload),
  publish: (id: number) => post<void>(`/papers/${id}/publish`),
  detail: (id: number) => get<PaperDetailResponse>(`/papers/${id}`),
}

export const examApi = {
  page: (params: Record<string, unknown> = {}) => get<PageResult<Exam>>('/exams', { page: 1, size: 100, ...params }),
  detail: (id: number) => get<Record<string, unknown>>(`/exams/${id}`),
  create: (payload: Record<string, unknown>) => post<Exam>('/exams', payload),
  update: (id: number, payload: Record<string, unknown>) => put<void>(`/exams/${id}`, payload),
  candidates: (id: number) => get<number[]>(`/exams/${id}/candidates`),
  removeEnded: (id: number) => del<void>(`/exams/${id}`),
  publish: (id: number) => post<void>(`/exams/${id}/publish`),
  start: (id: number) => post<Record<string, unknown>>(`/exams/${id}/start`),
  resume: (id: number) => get<Record<string, unknown>>(`/exams/${id}/attempt`),
  save: (id: number, payload: Record<string, unknown>) => post<Record<string, unknown>>(`/exams/${id}/save`, payload),
  submit: (id: number, attemptId: number, answers: unknown[]) => post(`/exams/${id}/submit`, answers, { attemptId }),
  event: (id: number, payload: Record<string, unknown>) => post<void>(`/exams/${id}/events`, payload),
}

export const gradeApi = {
  my: () => get<unknown[]>('/grades/my'),
  forExam: (examId: number) => get<unknown[]>(`/grades/exam/${examId}`),
  answers: (attemptId: number) => get<Record<string, unknown>>(`/grades/${attemptId}/answers`),
  score: (answerId: number, score: number, comment: string) => put(`/grades/answers/${answerId}`, { score, comment }),
  finalize: (attemptId: number) => post(`/grades/${attemptId}/finalize`),
  publish: (attemptId: number) => post(`/grades/${attemptId}/publish`),
  publishAll: (examId: number) => post<{ examId: number; publishedCount: number; alreadyPublishedCount: number }>(`/grades/exam/${examId}/publish-all`),
}

export interface AdminUser { id: number; username: string; nickname?: string; email?: string; phone?: string; status: number; lastLoginAt?: string; createdAt?: string; roles: string[] }
export interface UserRoleOption { code: string; name: string }
export const userApi = {
  page: (params: Record<string, unknown> = {}) => get<PageResult<AdminUser>>('/users', { page: 1, size: 10, ...params }),
  roleOptions: () => get<UserRoleOption[]>('/users/role-options'),
  detail: (id: number) => get<AdminUser>(`/users/${id}`),
  create: (data: Record<string, unknown>) => post<AdminUser>('/users', data),
  update: (id: number, data: Record<string, unknown>) => put<AdminUser>(`/users/${id}`, data),
  disable: (id: number) => del<{ id: number; status: number }>(`/users/${id}`),
}

export interface TeacherStatistics {
  teacherScope: string; examCount: number; paperCount: number; publishedAttemptCount: number; averageScoreRate: number;
  scoreDistribution: Array<{ label: string; count: number; percentage: number }>;
  paperDifficulties: Array<{ paperId: number; paperName: string; questionCount: number; totalScore: number; difficultyIndex: number; difficultyLabel: string; averageScoreRate?: number | null; basis: string }>
}
export const statisticsApi = {
  student: () => get<StudentStatistics>('/statistics/student'),
  teacher: (teacherId?: number) => get<TeacherStatistics>('/statistics/teacher', teacherId ? { teacherId } : undefined),
}

