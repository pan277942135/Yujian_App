export interface NormalizedFishBox {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

export interface FishDetection {
  confidence: number;
  box: NormalizedFishBox;
  label: string;
}

export type QualityLevel = 'GOOD' | 'WARNING' | 'INVALID';

export type FishInputStatus =
  | 'NONE'
  | 'PRIMARY'
  | 'MULTIPLE'
  | 'TOO_SMALL'
  | 'INCOMPLETE_FISH';

export interface FishInputAssessment {
  isClassifierEligible: boolean;
  qualityLevel: QualityLevel;
  qualityReason: string;
  status: FishInputStatus;
  primary: FishDetection | null;
  secondary: FishDetection | null;
  cropBox: NormalizedFishBox | null;
  bboxAreaRatio: number | null;
}

export interface RecognitionCandidate {
  classIndex: number;
  speciesKey: string;
  speciesName: string;
  confidence: number;
}

export interface RecognitionPrediction {
  modelVersion: string;
  modelSha256: string;
  top1: RecognitionCandidate;
  candidates: RecognitionCandidate[];
  latencyMs: number;
}

export interface ProductionRecognitionResult {
  status: FishInputStatus;
  assessment: FishInputAssessment;
  prediction: RecognitionPrediction | null;
  cropPixels: [number, number, number, number] | null; // left, top, right, bottom in original image coords
  latencyMs: number;
  imageUri: string;
  timestamp: number;
}

export interface FishGuideItem {
  id: string;
  nameCn: string;
  aliases: string[];
  category: string;
  scientificName: string;
  family?: string;
  genus?: string;
  coverImage?: string;
  discovered: boolean;
  catches: number;
  summary: string;
}

export interface CardContentFeature {
  title: string;
  text: string;
}

export interface FishKnowledgeCardContent {
  type: string;
  tag?: string;
  rarity?: number;
  power?: number;
  challenge?: number;
  description?: string;
  features?: CardContentFeature[];
  waterLayer?: string;
  behavior?: string;
  rod?: string;
  line?: string;
  hook?: string;
  bait?: string[];
  tip?: string;
}

export interface FishKnowledgeCard {
  id: number;
  speciesId: string;
  cardType: 'HERO' | 'IDENTIFICATION' | 'ECO' | 'GEAR' | 'SKILL';
  title: string;
  imageUrl?: string;
  description: string;
  content: FishKnowledgeCardContent;
  sortOrder: number;
  status: 'ACTIVE' | 'DRAFT';
}

export interface FishKnowledgeGalleryImage {
  id: number;
  type: string;
  url: string;
  title?: string;
  order: number;
}

export interface FishKnowledgeVideo {
  id: number;
  title: string;
  type: string;
  coverUrl: string | null;
  videoUrl: string;
  duration: number;
  tags: string[];
}

export interface FishKnowledgeSimilarity {
  speciesId: string;
  similarSpeciesId: string;
  similarSpeciesNameCn: string;
  difference: string;
}

export interface FishKnowledgeDetail {
  species: FishGuideItem;
  cards: FishKnowledgeCard[];
  gallery: FishKnowledgeGalleryImage[];
  profile: {
    bodyShape?: string;
    features: string[];
    habitat: string[];
    food?: string;
    season: string[];
  };
  fishing: {
    waterLayer?: string;
    season: string[];
    bait: string[];
    method: string[];
    summary: string;
  };
  videos: FishKnowledgeVideo[];
  similarity: FishKnowledgeSimilarity[];
  knowledge: {
    displayTag?: string;
    ecology: {
      waterLayer: string;
      behavior: string;
    };
    gear: {
      rod: string;
      line: string;
      hook: string;
      bait: string[];
    };
    skill: {
      tip: string;
    };
  };
}

export interface CatchRecord {
  id: string;
  speciesId: string;
  speciesName: string;
  confidence: number;
  imageUrl: string;
  weightKg: number;
  lengthCm: number;
  location: string;
  timeLabel: string;
  createdAt: string;
  note: string;
  isNewRecord: boolean;
  detectorBox?: NormalizedFishBox;
  cropBox?: NormalizedFishBox;
  assessment?: FishInputAssessment;
}

export interface UserSession {
  userId: string;
  username: string;
  nickname: string;
  accessToken: string;
  isLoggedIn: boolean;
}

export type SharePeriod = 'SINGLE' | 'TODAY' | 'WEEK' | 'MONTH' | 'YEAR' | 'ALL';

export interface CatchStatistics {
  totalCatches: number;
  speciesCount: number;
  topSpecies: Array<{ speciesId: string; speciesName: string; count: number }>;
}

export interface FeedbackSubmission {
  id: string;
  imageId: string;
  predictedSpeciesKey: string;
  correctSpeciesKey: string;
  userNote?: string;
  createdAt: string;
  synced: boolean;
}
