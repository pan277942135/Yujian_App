import React, { useState } from 'react';
import { ProductionRecognitionResult, CatchRecord, FishKnowledgeCard } from '../types';
import { KNOWLEDGE_DETAILS, INITIAL_SPECIES_LIST } from '../data/fallbackData';
import { YujianTopBar } from '../components/YujianTopBar';
import { DetectorOverlay } from '../components/DetectorOverlay';
import { FiveCardsViewer } from '../components/FiveCardsViewer';
import { SaveCatchModal } from '../components/SaveCatchModal';
import { enqueueFeedback } from '../services/storageService';
import {
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Plus,
  BookOpen,
  ThumbsUp,
  MessageSquare,
  Sparkles,
  ChevronRight,
  Info,
} from 'lucide-react';

interface RecognitionResultScreenProps {
  result: ProductionRecognitionResult;
  onBack: () => void;
  onViewSpeciesDetail: (speciesId: string) => void;
  onSaveCatch: (record: CatchRecord) => void;
}

export const RecognitionResultScreen: React.FC<RecognitionResultScreenProps> = ({
  result,
  onBack,
  onViewSpeciesDetail,
  onSaveCatch,
}) => {
  const [showSaveModal, setShowSaveModal] = useState(false);
  const [feedbackGiven, setFeedbackGiven] = useState<'CORRECT' | 'ERROR' | null>(null);
  const [showFeedbackInput, setShowFeedbackInput] = useState(false);
  const [feedbackNote, setFeedbackNote] = useState('');

  const top1 = result.prediction?.top1;
  const topSpeciesKey = top1?.speciesKey || 'yellow_catfish';
  const knowledge = KNOWLEDGE_DETAILS[topSpeciesKey] || KNOWLEDGE_DETAILS['crucian_carp'];
  const speciesMeta = knowledge?.species || INITIAL_SPECIES_LIST[0];

  const handleFeedback = (isCorrect: boolean) => {
    if (isCorrect) {
      setFeedbackGiven('CORRECT');
      enqueueFeedback({
        imageId: `img_${result.timestamp}`,
        predictedSpeciesKey: topSpeciesKey,
        correctSpeciesKey: topSpeciesKey,
        userNote: '用户确认AI识别准确',
      });
    } else {
      setShowFeedbackInput(true);
    }
  };

  const submitCorrection = () => {
    setFeedbackGiven('ERROR');
    enqueueFeedback({
      imageId: `img_${result.timestamp}`,
      predictedSpeciesKey: topSpeciesKey,
      correctSpeciesKey: feedbackNote || 'unknown',
      userNote: feedbackNote,
    });
    setShowFeedbackInput(false);
  };

  return (
    <div className="flex flex-col min-h-screen bg-[#F6F8F7] pb-24">
      <YujianTopBar
        title="识别结果"
        subtitle={`耗时 ${result.latencyMs}ms · ${result.prediction?.modelVersion || 'MODEL_M1'}`}
        onBack={onBack}
        rightAction={
          <button
            type="button"
            onClick={() => setShowSaveModal(true)}
            className="px-3.5 py-1.5 rounded-full bg-[#388478] text-white text-xs font-semibold flex items-center gap-1 hover:bg-[#2E6F65] shadow-xs active:scale-95"
          >
            <Plus size={14} />
            <span>记为鱼获</span>
          </button>
        }
      />

      <main className="p-4 flex flex-col gap-4 max-w-lg mx-auto w-full">
        {/* Visual Detector Box Overlay */}
        <DetectorOverlay
          imageUri={result.imageUri}
          detectorBox={result.assessment.primary?.box}
          cropBox={result.assessment.cropBox}
          assessment={result.assessment}
          speciesKey={topSpeciesKey}
        />

        {/* Quality Gate Status Card */}
        <div
          className={`p-3.5 rounded-2xl border flex items-start gap-3 ${
            result.assessment.qualityLevel === 'GOOD'
              ? 'bg-[#EAF4F2] border-[#388478]/20 text-[#172421]'
              : result.assessment.qualityLevel === 'WARNING'
              ? 'bg-[#FFF8EC] border-[#F29C38]/30 text-[#854D0E]'
              : 'bg-[#FDF2F2] border-[#D9534F]/30 text-[#991B1B]'
          }`}
        >
          <div className="mt-0.5">
            {result.assessment.qualityLevel === 'GOOD' && <CheckCircle2 size={18} className="text-[#388478]" />}
            {result.assessment.qualityLevel === 'WARNING' && <AlertTriangle size={18} className="text-[#F29C38]" />}
            {result.assessment.qualityLevel === 'INVALID' && <XCircle size={18} className="text-[#D9534F]" />}
          </div>
          <div className="flex-1 text-xs">
            <div className="font-bold mb-0.5">
              门禁评估: {result.assessment.qualityLevel === 'GOOD' ? '质量优良' : result.assessment.qualityLevel === 'WARNING' ? '质量提醒' : '质量不合格'}
              {result.assessment.bboxAreaRatio && (
                <span className="font-normal opacity-75 ml-2">
                  (鱼体占比 {(result.assessment.bboxAreaRatio * 100).toFixed(1)}%)
                </span>
              )}
            </div>
            <p className="leading-relaxed opacity-90">{result.assessment.qualityReason}</p>
          </div>
        </div>

        {/* Top 1 Primary Candidate Result Banner */}
        {top1 ? (
          <div className="bg-white rounded-3xl p-5 shadow-sm border border-[#748782]/10 flex flex-col gap-4">
            <div className="flex items-start justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <span className="px-2.5 py-0.5 rounded-full bg-[#388478]/10 text-[#388478] text-xs font-bold">
                    TOP 1 识别
                  </span>
                  <span className="text-xs text-[#748782] font-mono">{speciesMeta.scientificName}</span>
                </div>
                <h2 className="text-2xl font-black text-[#172421] mt-1 flex items-center gap-2">
                  {top1.speciesName}
                  <span className="text-sm font-bold text-[#D6B56D] bg-[#172421] px-2.5 py-0.5 rounded-full">
                    {(top1.confidence * 100).toFixed(0)}%
                  </span>
                </h2>
                {speciesMeta.aliases.length > 0 && (
                  <p className="text-xs text-[#748782] mt-1">
                    俗称：{speciesMeta.aliases.join(' / ')}
                  </p>
                )}
              </div>

              <button
                type="button"
                onClick={() => onViewSpeciesDetail(topSpeciesKey)}
                className="p-2.5 rounded-full bg-[#EAF4F2] text-[#388478] hover:bg-[#388478] hover:text-white transition-colors"
                title="查看图鉴详情"
              >
                <ChevronRight size={20} />
              </button>
            </div>

            {/* Top 3 Probability Breakdown */}
            {result.prediction && result.prediction.candidates.length > 1 && (
              <div className="pt-3 border-t border-[#748782]/10 flex flex-col gap-2">
                <span className="text-xs font-bold text-[#748782]">模型候选概率分布 (Softmax)</span>
                {result.prediction.candidates.slice(0, 3).map((candidate, idx) => (
                  <div key={candidate.speciesKey} className="flex items-center gap-2.5 text-xs">
                    <span className="w-5 font-mono text-[#748782] text-center font-semibold">#{idx + 1}</span>
                    <span className="w-16 font-semibold text-[#172421]">{candidate.speciesName}</span>
                    <div className="flex-1 h-2 bg-[#F6F8F7] rounded-full overflow-hidden">
                      <div
                        className={`h-full rounded-full transition-all duration-500 ${
                          idx === 0 ? 'bg-[#388478]' : 'bg-[#748782]/40'
                        }`}
                        style={{ width: `${Math.round(candidate.confidence * 100)}%` }}
                      />
                    </div>
                    <span className="w-10 text-right font-mono text-xs font-semibold text-[#172421]">
                      {(candidate.confidence * 100).toFixed(1)}%
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        ) : (
          <div className="bg-white rounded-3xl p-5 text-center shadow-sm">
            <Info size={32} className="mx-auto text-[#748782] mb-2" />
            <h3 className="text-base font-bold text-[#172421]">未能完成鱼种匹配</h3>
            <p className="text-xs text-[#748782] mt-1">
              当前画面质量未达分类门禁要求，建议重新拍摄并确保光线明亮。
            </p>
          </div>
        )}

        {/* 5 Knowledge Cards Carousel / Viewer */}
        {knowledge?.cards && knowledge.cards.length > 0 && (
          <div className="bg-white rounded-3xl p-5 shadow-sm border border-[#748782]/10">
            <FiveCardsViewer
              cards={knowledge.cards}
              onCardClick={() => onViewSpeciesDetail(topSpeciesKey)}
            />
          </div>
        )}

        {/* Real Feedback Submission Section */}
        <div className="bg-white rounded-3xl p-4 shadow-sm border border-[#748782]/10 flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-[#172421] flex items-center gap-1.5">
              <Sparkles size={14} className="text-[#388478]" />
              AI 闭环反馈通道 (离线/在线 Parity)
            </span>
            {feedbackGiven && (
              <span className="text-[11px] text-[#388478] font-bold">已记录反馈</span>
            )}
          </div>

          {!feedbackGiven && !showFeedbackInput && (
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => handleFeedback(true)}
                className="flex-1 py-2 px-3 rounded-xl bg-[#EAF4F2] text-[#388478] hover:bg-[#388478] hover:text-white text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors"
              >
                <ThumbsUp size={14} />
                <span>识别准确</span>
              </button>
              <button
                type="button"
                onClick={() => handleFeedback(false)}
                className="flex-1 py-2 px-3 rounded-xl bg-[#F6F8F7] text-[#748782] hover:bg-[#748782]/15 text-xs font-medium flex items-center justify-center gap-1.5 transition-colors"
              >
                <MessageSquare size={14} />
                <span>纠错 / 反馈</span>
              </button>
            </div>
          )}

          {showFeedbackInput && (
            <div className="flex flex-col gap-2 mt-1">
              <input
                type="text"
                value={feedbackNote}
                onChange={(e) => setFeedbackNote(e.target.value)}
                placeholder="请输入您认为正确的鱼种名称（如：军鱼、马口...）"
                className="px-3 py-2 text-xs rounded-xl border border-[#748782]/25 outline-none focus:border-[#388478]"
              />
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={submitCorrection}
                  className="flex-1 py-1.5 bg-[#388478] text-white text-xs font-semibold rounded-lg"
                >
                  提交纠错样本
                </button>
                <button
                  type="button"
                  onClick={() => setShowFeedbackInput(false)}
                  className="px-3 py-1.5 text-xs text-[#748782] rounded-lg border border-[#748782]/20"
                >
                  取消
                </button>
              </div>
            </div>
          )}
        </div>

        {/* View Full Knowledge Book Button */}
        <button
          type="button"
          onClick={() => onViewSpeciesDetail(topSpeciesKey)}
          className="w-full py-3.5 px-4 rounded-full bg-white border border-[#388478]/30 text-[#388478] font-bold text-sm flex items-center justify-center gap-2 hover:bg-[#EAF4F2] transition-colors shadow-xs"
        >
          <BookOpen size={18} />
          <span>查看《{top1?.speciesName || '鱼鉴'}》完整垂钓指南</span>
        </button>
      </main>

      {/* Save Catch Record Modal */}
      <SaveCatchModal
        isOpen={showSaveModal}
        onClose={() => setShowSaveModal(false)}
        recognitionResult={result}
        onSaved={onSaveCatch}
      />
    </div>
  );
};
