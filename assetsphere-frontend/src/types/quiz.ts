export type QuizDifficulty = "EASY" | "MEDIUM" | "HARD";

export interface GenerateQuizRequest {
  questionCount: number;
  difficulty: QuizDifficulty;
  topic?: string;
  modelId?: string;
}

export interface AiModelDescriptor {
  provider: string;
  modelId: string;
  displayName: string;
  capabilities: string[];
  minimumPlan: "FREE" | "PRO" | "ENTERPRISE";
  enabled: boolean;
}

export interface QuizQuestion {
  text: string;
  type: "MULTIPLE_CHOICE";
  options: string[];
  correctAnswer: string;
  explanation: string;
  sourceIds: string[];
}

export interface QuizResponse {
  title: string;
  questions: QuizQuestion[];
}
