export type User = {
  id: string
  name: string
  email: string
  token?: string | null
}

export type Analysis = {
  id: string
  title: string
  description: string | null
  stackTrace: string
  sourceCode: string
  errorCategory: string
  rootCause: string
  explanation: string
  evidence: string[]
  debuggingSteps: string[]
  suggestedFix: string
  testRecommendations: string[]
  analysisSource: 'DEMO' | 'BHARATCODE' | string
  verificationStatus: string
  createdAt: string
}

export type Verification = {
  status: 'PASSED' | 'FAILED' | 'INCONCLUSIVE' | string
  command: string
  passedTests: number
  failedTests: number
  durationMillis: number
  output: string
  message: string
  executedAt: string
}

export type ProviderStatus = {
  mode: 'AI_CONNECTED' | 'DEMO_MODE' | 'AI_UNAVAILABLE' | string
  message: string
  checkedAt: string
}
