import React, { useState, useEffect } from 'react';
import {
  FishGuideItem,
  CatchRecord,
  UserSession,
  ProductionRecognitionResult,
} from './types';
import {
  getStoredSession,
  saveStoredSession,
  clearStoredSession,
  getStoredCatches,
  saveCatchRecord,
  getStoredSpecies,
} from './services/storageService';
import { runFishRecognitionPipeline } from './services/fishRecognitionService';

// Screens
import { FishGuideHomeScreen } from './screens/FishGuideHomeScreen';
import { CameraCaptureScreen } from './screens/CameraCaptureScreen';
import { RecognizingScreen } from './screens/RecognizingScreen';
import { RecognitionResultScreen } from './screens/RecognitionResultScreen';
import { CatchDetailScreen } from './screens/CatchDetailScreen';
import { SpeciesDetailScreen } from './screens/SpeciesDetailScreen';
import { ShareCenterScreen } from './screens/ShareCenterScreen';
import { MyScreen } from './screens/MyScreen';
import { AuthScreens } from './screens/AuthScreens';

// Icons
import { Compass, Camera, User } from 'lucide-react';

type ActiveTab = 'GUIDE' | 'SCAN' | 'MY';
type ScreenView =
  | 'TAB_ROOT'
  | 'SPECIES_DETAIL'
  | 'RECOGNIZING'
  | 'RECOGNITION_RESULT'
  | 'CATCH_DETAIL'
  | 'SHARE_CENTER';

export const App: React.FC = () => {
  // Global State
  const [session, setSession] = useState<UserSession>(getStoredSession);
  const [speciesList, setSpeciesList] = useState<FishGuideItem[]>(getStoredSpecies);
  const [allCatches, setAllCatches] = useState<CatchRecord[]>(getStoredCatches);

  // Navigation State
  const [activeTab, setActiveTab] = useState<ActiveTab>('GUIDE');
  const [currentView, setCurrentView] = useState<ScreenView>('TAB_ROOT');

  // Sub-screen parameters
  const [selectedSpeciesId, setSelectedSpeciesId] = useState<string>('crucian_carp');
  const [selectedCatchRecord, setSelectedCatchRecord] = useState<CatchRecord | null>(null);
  const [recognitionResult, setRecognitionResult] = useState<ProductionRecognitionResult | null>(null);
  const [pendingResult, setPendingResult] = useState<ProductionRecognitionResult | null>(null);

  // Modals
  const [showAuthModal, setShowAuthModal] = useState<boolean>(false);

  // Sync species list on mount or when catches change
  const refreshAppData = () => {
    setSpeciesList(getStoredSpecies());
    setAllCatches(getStoredCatches());
    setSession(getStoredSession());
  };

  useEffect(() => {
    refreshAppData();
  }, []);

  // Handlers for Camera Scan & Pipeline
  const handleImageSelected = async (
    imageSource: string | HTMLCanvasElement,
    hintSpeciesKey?: string
  ) => {
    // Show recognizing animation screen
    setCurrentView('RECOGNIZING');

    try {
      // Start pipeline concurrently with animation
      const result = await runFishRecognitionPipeline(imageSource, hintSpeciesKey);
      setPendingResult(result);
    } catch (err) {
      console.error('Recognition error', err);
      // Fallback result if needed
      const fallbackResult = await runFishRecognitionPipeline(imageSource, 'crucian_carp');
      setPendingResult(fallbackResult);
    }
  };

  const handleRecognizingAnimationComplete = () => {
    if (pendingResult) {
      setRecognitionResult(pendingResult);
      setPendingResult(null);
      setCurrentView('RECOGNITION_RESULT');
    }
  };

  const handleSaveCatch = (newRecord: CatchRecord) => {
    const updated = saveCatchRecord(newRecord);
    setAllCatches(updated);
    setSpeciesList(getStoredSpecies());
    setSelectedCatchRecord(newRecord);
    setCurrentView('CATCH_DETAIL');
  };

  const handleOpenCatchDetail = (record: CatchRecord) => {
    setSelectedCatchRecord(record);
    setCurrentView('CATCH_DETAIL');
  };

  const handleOpenSpeciesDetail = (speciesId: string) => {
    setSelectedSpeciesId(speciesId);
    setCurrentView('SPECIES_DETAIL');
  };

  const handleOpenShareCenter = (record?: CatchRecord) => {
    if (record) {
      setSelectedCatchRecord(record);
    }
    setCurrentView('SHARE_CENTER');
  };

  const handleLoginSuccess = (newSession: UserSession) => {
    saveStoredSession(newSession);
    setSession(newSession);
    setShowAuthModal(false);
  };

  const handleLogout = () => {
    clearStoredSession();
    const guest: UserSession = {
      userId: 'guest_user',
      username: 'guest',
      nickname: '未登录钓友',
      accessToken: '',
      isLoggedIn: false,
    };
    setSession(guest);
  };

  // Render current active screen
  const renderScreen = () => {
    // 1. Full-screen Sub-views
    if (currentView === 'RECOGNIZING') {
      return <RecognizingScreen onCompleted={handleRecognizingAnimationComplete} />;
    }

    if (currentView === 'RECOGNITION_RESULT' && recognitionResult) {
      return (
        <RecognitionResultScreen
          result={recognitionResult}
          onBack={() => {
            setCurrentView('TAB_ROOT');
            setActiveTab('SCAN');
          }}
          onViewSpeciesDetail={(spId) => {
            setSelectedSpeciesId(spId);
            setCurrentView('SPECIES_DETAIL');
          }}
          onSaveCatch={handleSaveCatch}
        />
      );
    }

    if (currentView === 'SPECIES_DETAIL') {
      return (
        <SpeciesDetailScreen
          speciesId={selectedSpeciesId}
          onBack={() => setCurrentView('TAB_ROOT')}
          onOpenScanner={() => {
            setCurrentView('TAB_ROOT');
            setActiveTab('SCAN');
          }}
          onSelectSimilarSpecies={(id) => setSelectedSpeciesId(id)}
        />
      );
    }

    if (currentView === 'CATCH_DETAIL' && selectedCatchRecord) {
      return (
        <CatchDetailScreen
          catchRecord={selectedCatchRecord}
          onBack={() => setCurrentView('TAB_ROOT')}
          onOpenShare={(c) => {
            setSelectedCatchRecord(c);
            setCurrentView('SHARE_CENTER');
          }}
          onViewSpecies={(spId) => {
            setSelectedSpeciesId(spId);
            setCurrentView('SPECIES_DETAIL');
          }}
        />
      );
    }

    if (currentView === 'SHARE_CENTER') {
      return (
        <ShareCenterScreen
          initialCatch={selectedCatchRecord || undefined}
          allCatches={allCatches}
          session={session}
          onBack={() => setCurrentView('TAB_ROOT')}
        />
      );
    }

    // 2. Tab Root Views
    switch (activeTab) {
      case 'GUIDE':
        return (
          <FishGuideHomeScreen
            speciesList={speciesList}
            onSelectSpecies={handleOpenSpeciesDetail}
            onOpenScanner={() => setActiveTab('SCAN')}
          />
        );
      case 'SCAN':
        return <CameraCaptureScreen onImageSelected={handleImageSelected} />;
      case 'MY':
        return (
          <MyScreen
            session={session}
            allCatches={allCatches}
            onOpenCatchDetail={handleOpenCatchDetail}
            onOpenShareCenter={() => handleOpenShareCenter()}
            onOpenAuth={() => setShowAuthModal(true)}
            onLogout={handleLogout}
            onViewGuide={() => setActiveTab('GUIDE')}
          />
        );
      default:
        return null;
    }
  };

  const isTabRoot = currentView === 'TAB_ROOT';

  return (
    <div className="app-viewport flex flex-col min-h-screen relative font-sans">
      {/* Active Screen Content */}
      <div className="flex-1 w-full">{renderScreen()}</div>

      {/* Bottom Tab Bar (Visible on root tabs) */}
      {isTabRoot && (
        <nav
          id="yujian-bottom-tabbar"
          className="fixed bottom-0 inset-x-0 max-w-[480px] mx-auto z-40 bg-white/95 backdrop-blur-md border-t border-[#748782]/15 px-6 py-2 shadow-lg"
          aria-label="主要导航"
        >
          <div className="flex items-center justify-between relative">
            {/* Tab 1: 鱼鉴 */}
            <button
              type="button"
              onClick={() => setActiveTab('GUIDE')}
              className={`flex flex-col items-center gap-1 py-1 px-4 rounded-2xl transition-all ${
                activeTab === 'GUIDE'
                  ? 'text-[#388478] font-bold scale-105'
                  : 'text-[#748782] font-medium hover:text-[#172421]'
              }`}
            >
              <Compass size={22} className={activeTab === 'GUIDE' ? 'stroke-[2.5]' : ''} />
              <span className="text-[11px]">鱼鉴</span>
            </button>

            {/* Tab 2: 识鱼 (Prominent central button) */}
            <div className="relative -top-5">
              <button
                type="button"
                onClick={() => setActiveTab('SCAN')}
                className={`w-14 h-14 rounded-full flex items-center justify-center text-white shadow-xl transition-transform active:scale-95 ${
                  activeTab === 'SCAN'
                    ? 'bg-[#172421] ring-4 ring-[#388478]/30 scale-105'
                    : 'bg-[#388478] shadow-[#388478]/35 hover:bg-[#2E6F65]'
                }`}
                aria-label="相机识别"
              >
                <Camera size={26} />
              </button>
              <span className="absolute -bottom-4 inset-x-0 text-center text-[10px] font-bold text-[#388478]">
                AI 识鱼
              </span>
            </div>

            {/* Tab 3: 我的 */}
            <button
              type="button"
              onClick={() => setActiveTab('MY')}
              className={`flex flex-col items-center gap-1 py-1 px-4 rounded-2xl transition-all ${
                activeTab === 'MY'
                  ? 'text-[#388478] font-bold scale-105'
                  : 'text-[#748782] font-medium hover:text-[#172421]'
              }`}
            >
              <User size={22} className={activeTab === 'MY' ? 'stroke-[2.5]' : ''} />
              <span className="text-[11px]">我的</span>
            </button>
          </div>
        </nav>
      )}

      {/* Auth Modal */}
      {showAuthModal && (
        <AuthScreens
          onSuccess={handleLoginSuccess}
          onCancel={() => setShowAuthModal(false)}
        />
      )}
    </div>
  );
};
