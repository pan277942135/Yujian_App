import { CatchRecord, UserSession, FeedbackSubmission, CatchStatistics, FishGuideItem } from '../types';
import { INITIAL_CATCH_RECORDS, INITIAL_SPECIES_LIST } from '../data/fallbackData';

const STORAGE_KEYS = {
  SESSION: 'yujian_user_session',
  CATCHES: 'yujian_catches_v1',
  SPECIES: 'yujian_species_v1',
  FEEDBACK: 'yujian_offline_feedback_v1',
};

const DEFAULT_SESSION: UserSession = {
  userId: 'angler_guest_001',
  username: 'yujian_angler',
  nickname: '江畔钓客',
  accessToken: 'demo_token_yujian_7e8e',
  isLoggedIn: true,
};

export function getStoredSession(): UserSession {
  try {
    const raw = localStorage.getItem(STORAGE_KEYS.SESSION);
    if (raw) return JSON.parse(raw);
  } catch (e) {
    console.error('Failed to read stored session', e);
  }
  return DEFAULT_SESSION;
}

export function saveStoredSession(session: UserSession): void {
  try {
    localStorage.setItem(STORAGE_KEYS.SESSION, JSON.stringify(session));
  } catch (e) {
    console.error('Failed to save session', e);
  }
}

export function clearStoredSession(): void {
  try {
    localStorage.removeItem(STORAGE_KEYS.SESSION);
  } catch (e) {
    console.error('Failed to clear session', e);
  }
}

export function getStoredCatches(): CatchRecord[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEYS.CATCHES);
    if (raw) return JSON.parse(raw);
  } catch (e) {
    console.error('Failed to read catches', e);
  }
  return INITIAL_CATCH_RECORDS;
}

export function saveCatchRecord(record: CatchRecord): CatchRecord[] {
  const current = getStoredCatches();
  const updated = [record, ...current];
  try {
    localStorage.setItem(STORAGE_KEYS.CATCHES, JSON.stringify(updated));
  } catch (e) {
    console.error('Failed to save catch record', e);
  }
  // also mark species as discovered
  recordDiscovery(record.speciesId);
  return updated;
}

export function getStoredSpecies(): FishGuideItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEYS.SPECIES);
    if (raw) {
      const storedMap: Record<string, Partial<FishGuideItem>> = JSON.parse(raw);
      return INITIAL_SPECIES_LIST.map((sp) => ({
        ...sp,
        discovered: storedMap[sp.id]?.discovered ?? sp.discovered,
        catches: storedMap[sp.id]?.catches ?? sp.catches,
      }));
    }
  } catch (e) {
    console.error('Failed to read species', e);
  }
  return INITIAL_SPECIES_LIST;
}

export function recordDiscovery(speciesId: string): void {
  try {
    const current = getStoredSpecies();
    const target = current.find((s) => s.id === speciesId);
    if (target) {
      target.discovered = true;
      target.catches += 1;
      const map: Record<string, { discovered: boolean; catches: number }> = {};
      current.forEach((s) => {
        map[s.id] = { discovered: s.discovered, catches: s.catches };
      });
      localStorage.setItem(STORAGE_KEYS.SPECIES, JSON.stringify(map));
    }
  } catch (e) {
    console.error('Failed to record discovery', e);
  }
}

export function calculateCatchStatistics(catches: CatchRecord[]): CatchStatistics {
  const speciesCounts: Record<string, { name: string; count: number }> = {};
  catches.forEach((c) => {
    if (!speciesCounts[c.speciesId]) {
      speciesCounts[c.speciesId] = { name: c.speciesName, count: 0 };
    }
    speciesCounts[c.speciesId].count += 1;
  });

  const topSpecies = Object.entries(speciesCounts)
    .map(([speciesId, info]) => ({
      speciesId,
      speciesName: info.name,
      count: info.count,
    }))
    .sort((a, b) => b.count - a.count);

  const uniqueSpecies = new Set(catches.map((c) => c.speciesId));

  return {
    totalCatches: catches.length,
    speciesCount: uniqueSpecies.size,
    topSpecies,
  };
}

export function getStoredFeedbackQueue(): FeedbackSubmission[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEYS.FEEDBACK);
    if (raw) return JSON.parse(raw);
  } catch (e) {
    console.error('Failed to read feedback queue', e);
  }
  return [];
}

export function enqueueFeedback(submission: Omit<FeedbackSubmission, 'id' | 'createdAt' | 'synced'>): FeedbackSubmission {
  const item: FeedbackSubmission = {
    ...submission,
    id: `fb_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
    createdAt: new Date().toISOString(),
    synced: true, // In web app, we simulate immediate successful transport
  };

  const queue = getStoredFeedbackQueue();
  queue.unshift(item);
  try {
    localStorage.setItem(STORAGE_KEYS.FEEDBACK, JSON.stringify(queue.slice(0, 50)));
  } catch (e) {
    console.error('Failed to enqueue feedback', e);
  }
  return item;
}
