export type Role = 'STUDENT' | 'TEACHER' | 'ADMIN'

export interface UserProfile {
  userId: number
  username: string
  nickname?: string
  email?: string
  phone?: string
  roles: Role[]
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  current?: number
  size?: number
  pages?: number
}

export interface Subject {
  id: number
  name: string
  description?: string
  status?: number
  sort?: number
}

export interface KnowledgePoint {
  id: number
  subjectId: number
  parentId?: number | null
  name: string
  description?: string
  sort?: number
  status?: number
}

export interface QuestionOption {
  id?: number
  key?: string
  optionKey?: string
  content?: string
  optionContent?: string
  sort?: number
  correct?: boolean
}

/**
 * Union of the two question shapes the API returns.
 *
 * The paged list (`GET /questions`) returns the question bank metadata plus
 * `hasAnalysis`; it never returns `analysis` or `answerConfig`, because any
 * authenticated account may list questions.
 *
 * `GET /questions/{id}` returns the full detail, where `analysis`,
 * `answerConfig` and `options[].correct` are present only for accounts holding
 * `question:create` or `question:update`.
 */
export interface Question {
  id: number
  subjectId?: number
  knowledgePointId?: number
  type: string
  content: string
  /** List rows carry only this flag; the analysis text itself needs teacher rights. */
  hasAnalysis?: boolean
  analysis?: string
  difficulty: string
  difficultyValue?: number
  defaultScore?: number
  options?: QuestionOption[]
  answerConfig?: string
  tags?: string
  status?: number
  createdAt?: string
}

export interface Paper {
  id: number
  name: string
  subjectId?: number
  type: 'FIXED' | 'RULE' | string
  totalScore: number
  duration: number
  status: string
  ruleConfig?: string | Record<string, unknown> | null
  creatorId?: number
  questions?: Array<Record<string, unknown>>
  createdAt?: string
}

export interface Exam {
  id: number
  name: string
  paperId: number
  startTime: string
  endTime: string
  duration: number
  maxAttempts: number
  candidateScope: string
  status: string
  creatorId?: number
}

export interface PracticeQuestion {
  id: number
  type: string
  content: string
  difficulty: string
  defaultScore: number
  options: Array<{ id: number; key: string; content: string; sort: number }>
}

export interface ExamQuestion {
  paperQuestionId: number
  questionNo: number
  score: number
  content: string
  type: string
  difficulty: string
  options: Array<{ optionKey: string; optionContent: string; sort: number }>
  savedAnswer?: string
}

export interface StudentStatistics {
  totalPractice: number
  correctPractice: number
  wrongPractice: number
  practiceAccuracy: number
  wrongQuestionCount: number
  favoriteCount: number
  examCount: number
  averageScore: number
  highestScore: number
  knowledgePoints: Array<{ knowledgePointId: number; knowledgePointName: string; attempts: number; correct: number; accuracy: number }>
  scoreTrend: Array<{ examAttemptId: number; examId: number; attemptNo: number; score: number }>
}
