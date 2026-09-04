import React, { useState } from 'react';
import { CatchRecord, UserSession, SharePeriod } from '../types';
import { YujianTopBar } from '../components/YujianTopBar';
import { FishIllustration } from '../components/FishIllustration';
import { calculateCatchStatistics } from '../services/storageService';
import confetti from 'canvas-confetti';
import {
  Share2,
  Download,
  Copy,
  Check,
  Trophy,
  Scale,
  Ruler,
  Calendar,
  Sparkles,
  MapPin,
  QrCode,
} from 'lucide-react';

interface ShareCenterScreenProps {
  initialCatch?: CatchRecord;
  allCatches: CatchRecord[];
  session: UserSession;
  onBack: () => void;
}

export const ShareCenterScreen: React.FC<ShareCenterScreenProps> = ({
  initialCatch,
  allCatches,
  session,
  onBack,
}) => {
  const [sharePeriod, setSharePeriod] = useState<SharePeriod>(initialCatch ? 'SINGLE' : 'ALL');
  const [selectedCatch, setSelectedCatch] = useState<CatchRecord | undefined>(
    initialCatch || allCatches[0]
  );
  const [copied, setCopied] = useState(false);
  const [downloaded, setDownloaded] = useState(false);

  const stats = calculateCatchStatistics(allCatches);

  const handleShareClick = () => {
    try {
      confetti({
        particleCount: 75,
        spread: 70,
        origin: { y: 0.6 },
        colors: ['#388478', '#D6B56D', '#2E6F65', '#FBE6B6'],
      });
    } catch (e) {
      // safe fallback
    }

    if (navigator.share) {
      navigator
        .share({
          title: `【渔见AI战报】${session.nickname}的钓获成绩单`,
          text: `我今天在渔见AI记录了${selectedCatch ? selectedCatch.speciesName : '今日鱼获'}，快来比比看！`,
          url: window.location.href,
        })
        .catch(() => {});
    } else {
      handleCopyLink();
    }
  };

  const handleCopyLink = () => {
    try {
      navigator.clipboard.writeText(window.location.href);
      setCopied(true);
      setTimeout(() => setCopied(false), 2500);
    } catch (e) {
      setCopied(true);
      setTimeout(() => setCopied(false), 2500);
    }
  };

  const handleDownloadPoster = () => {
    setDownloaded(true);
    setTimeout(() => setDownloaded(false), 3000);
    try {
      confetti({
        particleCount: 50,
        spread: 60,
        origin: { y: 0.7 },
      });
    } catch (e) {}
  };

  return (
    <div className="flex flex-col min-h-screen bg-[#F6F8F7] pb-24">
      <YujianTopBar title="分享海报中心" subtitle="战报生成器" onBack={onBack} />

      <main className="p-4 flex flex-col gap-4 max-w-lg mx-auto w-full">
        {/* Period Selector Tabs */}
        <div className="flex items-center gap-1.5 p-1 bg-white rounded-2xl border border-[#748782]/10 overflow-x-auto no-scrollbar">
          {[
            { id: 'SINGLE', label: '单尾记录' },
            { id: 'TODAY', label: '今日战报' },
            { id: 'WEEK', label: '本周总揽' },
            { id: 'ALL', label: '年度全览' },
          ].map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setSharePeriod(tab.id as SharePeriod)}
              className={`flex-1 py-2 px-3 rounded-xl text-xs font-semibold whitespace-nowrap transition-colors ${
                sharePeriod === tab.id
                  ? 'bg-[#388478] text-white shadow-xs'
                  : 'text-[#748782] hover:text-[#172421]'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* If SINGLE mode, pick catch record */}
        {sharePeriod === 'SINGLE' && allCatches.length > 0 && (
          <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar">
            {allCatches.map((c) => {
              const isSelected = selectedCatch?.id === c.id;
              return (
                <button
                  key={c.id}
                  type="button"
                  onClick={() => setSelectedCatch(c)}
                  className={`flex items-center gap-2 p-2 rounded-2xl border transition-all flex-shrink-0 ${
                    isSelected
                      ? 'bg-[#EAF4F2] border-[#388478] text-[#388478]'
                      : 'bg-white border-[#748782]/10 text-[#172421]'
                  }`}
                >
                  <div className="w-8 h-8 rounded-xl bg-[#F6F8F7] flex items-center justify-center">
                    <FishIllustration size={28} speciesKey={c.speciesId} />
                  </div>
                  <div className="text-left text-xs">
                    <span className="font-bold block">{c.speciesName}</span>
                    <span className="text-[10px] text-[#748782]">{c.weightKg} kg</span>
                  </div>
                </button>
              );
            })}
          </div>
        )}

        {/* POSTER CARD (Visual poster preview) */}
        <div
          id="yujian-share-poster"
          className="relative bg-gradient-to-b from-[#172421] via-[#1E2E2A] to-[#121A18] rounded-[32px] p-6 text-white shadow-2xl border border-white/10 overflow-hidden flex flex-col gap-5"
        >
          {/* Subtle background wave effect */}
          <div className="absolute -right-12 -top-12 w-48 h-48 rounded-full bg-[#388478]/20 blur-3xl pointer-events-none" />
          <div className="absolute -left-12 -bottom-12 w-48 h-48 rounded-full bg-[#D6B56D]/15 blur-3xl pointer-events-none" />

          {/* Top Brand Bar */}
          <div className="flex items-center justify-between border-b border-white/10 pb-4 relative z-10">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-full bg-[#388478] flex items-center justify-center font-black text-white text-xs">
                见
              </div>
              <div>
                <h3 className="text-sm font-black tracking-wide">渔见 AI · 钓获战报</h3>
                <span className="text-[10px] text-[#748782] block font-mono">
                  {new Date().toLocaleDateString('zh-CN')} · 渔见水域档案
                </span>
              </div>
            </div>

            <span className="text-[10px] font-bold bg-[#D6B56D] text-[#172421] px-2 py-0.5 rounded-full flex items-center gap-1 shadow-xs">
              <Trophy size={10} />
              战绩认证
            </span>
          </div>

          {/* Angler Profile Banner */}
          <div className="flex items-center gap-3 relative z-10">
            <div className="w-12 h-12 rounded-full bg-gradient-to-tr from-[#388478] to-[#D6B56D] p-0.5 flex items-center justify-center">
              <div className="w-full h-full rounded-full bg-[#172421] flex items-center justify-center font-black text-white text-base">
                {session.nickname.slice(0, 1)}
              </div>
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <span className="text-base font-black text-white">{session.nickname}</span>
                <span className="text-[10px] text-[#5F9386] font-mono">ID: {session.username}</span>
              </div>
              <p className="text-xs text-[#748782] mt-0.5">
                {sharePeriod === 'SINGLE'
                  ? `在 ${selectedCatch?.location || '江河水域'} 擒获巨物`
                  : `累计斩获 ${stats.totalCatches} 尾鱼获 · 点亮 ${stats.speciesCount} 种鱼类图鉴`}
              </p>
            </div>
          </div>

          {/* Center Graphic */}
          <div className="relative z-10 bg-white/5 rounded-3xl p-5 border border-white/10 flex flex-col items-center justify-center text-center">
            {sharePeriod === 'SINGLE' && selectedCatch ? (
              <>
                <div className="w-36 h-36 flex items-center justify-center">
                  <FishIllustration size={130} speciesKey={selectedCatch.speciesId} />
                </div>
                <div className="mt-2">
                  <span className="text-xs font-bold text-[#5F9386] bg-[#388478]/20 px-3 py-0.5 rounded-full">
                    AI 算法认定置信度 {selectedCatch.confidence}%
                  </span>
                  <h4 className="text-2xl font-black text-white mt-1.5">
                    {selectedCatch.speciesName}
                  </h4>
                </div>

                {/* Metrics */}
                <div className="grid grid-cols-2 gap-3 w-full mt-4 pt-3 border-t border-white/10">
                  <div className="bg-black/20 p-2.5 rounded-2xl">
                    <span className="text-[10px] text-[#748782] flex items-center justify-center gap-1">
                      <Scale size={11} className="text-[#5F9386]" />
                      重 量
                    </span>
                    <span className="text-base font-black text-white mt-0.5 block font-mono">
                      {selectedCatch.weightKg} kg
                    </span>
                  </div>

                  <div className="bg-black/20 p-2.5 rounded-2xl">
                    <span className="text-[10px] text-[#748782] flex items-center justify-center gap-1">
                      <Ruler size={11} className="text-[#5F9386]" />
                      长 度
                    </span>
                    <span className="text-base font-black text-white mt-0.5 block font-mono">
                      {selectedCatch.lengthCm} cm
                    </span>
                  </div>
                </div>
              </>
            ) : (
              <div className="w-full py-4 flex flex-col items-center">
                <div className="flex items-center justify-center gap-2 mb-2">
                  <Trophy size={32} className="text-[#D6B56D]" />
                </div>
                <h4 className="text-lg font-black text-white">荣耀钓获成绩单</h4>
                <div className="grid grid-cols-2 gap-3 w-full mt-4">
                  <div className="bg-black/20 p-3 rounded-2xl text-center">
                    <span className="text-xs text-[#748782]">总鱼获数量</span>
                    <span className="text-xl font-black text-white block mt-1 font-mono">
                      {stats.totalCatches} 尾
                    </span>
                  </div>
                  <div className="bg-black/20 p-3 rounded-2xl text-center">
                    <span className="text-xs text-[#748782]">点亮鱼种数</span>
                    <span className="text-xl font-black text-[#D6B56D] block mt-1 font-mono">
                      {stats.speciesCount} 种
                    </span>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Bottom Watermark / QR Code */}
          <div className="flex items-center justify-between pt-2 border-t border-white/10 relative z-10 text-xs">
            <div className="flex items-center gap-2 text-[#748782]">
              <QrCode size={36} className="text-white/80" />
              <div>
                <span className="text-[11px] text-white font-bold block">渔见 AI 垂钓助手</span>
                <span className="text-[9px] block">长按扫码识别你钓到的鱼</span>
              </div>
            </div>

            <div className="text-right text-[10px] text-[#748782]">
              <span className="block font-semibold text-white/90">权威鱼类分类算法</span>
              <span>YOLOX + M1 Model v0.2</span>
            </div>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex flex-col gap-2.5 pt-2">
          <button
            type="button"
            onClick={handleShareClick}
            className="w-full py-3.5 px-4 rounded-full bg-[#388478] text-white font-semibold text-sm flex items-center justify-center gap-2 hover:bg-[#2E6F65] active:scale-[0.98] transition-transform shadow-md shadow-[#388478]/25"
          >
            <Share2 size={18} />
            <span>分享战报给钓友群</span>
          </button>

          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={handleDownloadPoster}
              className="py-3 px-4 rounded-full bg-white border border-[#748782]/15 text-[#172421] font-semibold text-xs flex items-center justify-center gap-1.5 hover:bg-[#EAF4F2] active:scale-95 transition-all"
            >
              {downloaded ? (
                <>
                  <Check size={16} className="text-[#388478]" />
                  <span className="text-[#388478]">海报已存至相册</span>
                </>
              ) : (
                <>
                  <Download size={16} className="text-[#748782]" />
                  <span>保存战报海报</span>
                </>
              )}
            </button>

            <button
              type="button"
              onClick={handleCopyLink}
              className="py-3 px-4 rounded-full bg-white border border-[#748782]/15 text-[#172421] font-semibold text-xs flex items-center justify-center gap-1.5 hover:bg-[#EAF4F2] active:scale-95 transition-all"
            >
              {copied ? (
                <>
                  <Check size={16} className="text-[#388478]" />
                  <span className="text-[#388478]">战报链接已复制</span>
                </>
              ) : (
                <>
                  <Copy size={16} className="text-[#748782]" />
                  <span>复制战报链接</span>
                </>
              )}
            </button>
          </div>
        </div>
      </main>
    </div>
  );
};
