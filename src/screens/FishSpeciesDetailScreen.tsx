import React, { useState } from 'react';
import { FishKnowledgeDetail, CatchRecord } from '../types';
import { KNOWLEDGE_DETAILS, INITIAL_SPECIES_LIST } from '../data/fallbackData';
import { YujianTopBar } from '../components/YujianTopBar';
import { HeroCarousel } from '../components/HeroCarousel';
import { FiveCardsViewer } from '../components/FiveCardsViewer';
import { FishIllustration } from '../components/FishIllustration';
import {
  Fish,
  Layers,
  HelpCircle,
  Trophy,
  ChevronRight,
} from 'lucide-react';

interface FishSpeciesDetailScreenProps {
  speciesId: string;
  userCatches: CatchRecord[];
  onBack: () => void;
  onSelectCatch: (record: CatchRecord) => void;
  onOpenScanner: () => void;
}

export const FishSpeciesDetailScreen: React.FC<FishSpeciesDetailScreenProps> = ({
  speciesId,
  userCatches,
  onBack,
  onSelectCatch,
  onOpenScanner,
}) => {
  const [activeTab, setActiveTab] = useState<'INFO' | 'CATCHES' | 'RANK'>('INFO');

  const detail: FishKnowledgeDetail =
    KNOWLEDGE_DETAILS[speciesId] ||
    KNOWLEDGE_DETAILS['crucian_carp'] ||
    ({
      species: INITIAL_SPECIES_LIST[0],
      cards: [],
      gallery: [],
      profile: { features: [], habitat: [], season: [] },
      fishing: { season: [], bait: [], method: [], summary: '' },
      videos: [],
      similarity: [],
      knowledge: {
        ecology: { waterLayer: '', behavior: '' },
        gear: { rod: '', line: '', hook: '', bait: [] },
        skill: { tip: '' },
      },
    } as FishKnowledgeDetail);

  const species = detail.species;
  const filteredCatches = userCatches.filter((c) => c.speciesId === speciesId);

  return (
    <div className="flex flex-col min-h-screen bg-[#F6F8F7] pb-24">
      <YujianTopBar
        title={species.nameCn}
        subtitle={species.scientificName}
        onBack={onBack}
        rightAction={
          <button
            type="button"
            onClick={onOpenScanner}
            className="px-3 py-1.5 rounded-full bg-[#388478] text-white text-xs font-semibold hover:bg-[#2E6F65] active:scale-95 transition-transform"
          >
            识鱼
          </button>
        }
      />

      <main className="p-4 flex flex-col gap-4 max-w-lg mx-auto w-full">
        {/* Top Hero Cards Carousel */}
        <HeroCarousel
          cards={detail.cards}
          speciesKey={species.id}
        />

        {/* 3-Tab Segment Bar */}
        <div className="flex items-center p-1 rounded-2xl bg-white border border-[#748782]/15 shadow-xs">
          <button
            type="button"
            onClick={() => setActiveTab('INFO')}
            className={`flex-1 py-2 text-xs font-bold rounded-xl transition-all ${
              activeTab === 'INFO'
                ? 'bg-[#388478] text-white shadow-xs'
                : 'text-[#748782] hover:text-[#172421]'
            }`}
          >
            鱼种介绍
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('CATCHES')}
            className={`flex-1 py-2 text-xs font-bold rounded-xl transition-all ${
              activeTab === 'CATCHES'
                ? 'bg-[#388478] text-white shadow-xs'
                : 'text-[#748782] hover:text-[#172421]'
            }`}
          >
            我的鱼获 ({filteredCatches.length})
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('RANK')}
            className={`flex-1 py-2 text-xs font-bold rounded-xl transition-all ${
              activeTab === 'RANK'
                ? 'bg-[#388478] text-white shadow-xs'
                : 'text-[#748782] hover:text-[#172421]'
            }`}
          >
            钓手榜单
          </button>
        </div>

        {/* TAB 1: 鱼种介绍 */}
        {activeTab === 'INFO' && (
          <div className="flex flex-col gap-4">
            {/* Five Cards Preview */}
            <div className="bg-white rounded-3xl p-5 shadow-sm border border-[#748782]/10">
              <FiveCardsViewer cards={detail.cards} />
            </div>

            {/* Structured Profile */}
            <div className="bg-white rounded-3xl p-5 shadow-sm border border-[#748782]/10 flex flex-col gap-3">
              <h3 className="text-base font-bold text-[#172421] flex items-center gap-2">
                <Fish size={18} className="text-[#388478]" />
                生物学档案与习性
              </h3>

              {detail.profile.bodyShape && (
                <div className="text-xs text-[#172421] flex items-center gap-2 py-1 border-b border-[#748782]/10">
                  <span className="font-bold text-[#748782] w-20 flex-shrink-0">体型形态:</span>
                  <span>{detail.profile.bodyShape}</span>
                </div>
              )}

              {detail.profile.food && (
                <div className="text-xs text-[#172421] flex items-center gap-2 py-1 border-b border-[#748782]/10">
                  <span className="font-bold text-[#748782] w-20 flex-shrink-0">主要食物:</span>
                  <span>{detail.profile.food}</span>
                </div>
              )}

              {detail.profile.habitat && detail.profile.habitat.length > 0 && (
                <div className="text-xs text-[#172421] flex items-center gap-2 py-1 border-b border-[#748782]/10">
                  <span className="font-bold text-[#748782] w-20 flex-shrink-0">常见栖息地:</span>
                  <div className="flex flex-wrap gap-1">
                    {detail.profile.habitat.map((h, i) => (
                      <span key={i} className="px-2 py-0.5 rounded-md bg-[#EAF4F2] text-[#388478] text-[11px]">
                        {h}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {detail.profile.features && detail.profile.features.length > 0 && (
                <div className="text-xs text-[#172421] flex items-start gap-2 py-1">
                  <span className="font-bold text-[#748782] w-20 flex-shrink-0 pt-0.5">识别特征:</span>
                  <div className="flex flex-wrap gap-1.5 flex-1">
                    {detail.profile.features.map((f, i) => (
                      <span key={i} className="px-2 py-0.5 rounded-md bg-[#F6F8F7] text-[#172421] text-[11px] border border-[#748782]/10">
                        {f}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* How to Fish (作钓攻略) */}
            <div className="bg-white rounded-3xl p-5 shadow-sm border border-[#748782]/10 flex flex-col gap-3">
              <h3 className="text-base font-bold text-[#172421] flex items-center gap-2">
                <Layers size={18} className="text-[#388478]" />
                垂钓实战方案
              </h3>

              <div className="bg-[#EAF4F2] rounded-2xl p-3.5 text-xs text-[#172421]">
                <div className="font-bold text-[#388478] mb-1">推荐水层与环境</div>
                <p>{detail.fishing.waterLayer || detail.knowledge?.ecology?.waterLayer || '底层至中下层水域'}</p>
              </div>

              {detail.fishing.bait && detail.fishing.bait.length > 0 && (
                <div className="text-xs">
                  <span className="font-bold text-[#748782]">推荐饵料配方:</span>
                  <div className="flex flex-wrap gap-1.5 mt-1.5">
                    {detail.fishing.bait.map((b, i) => (
                      <span key={i} className="px-2.5 py-1 rounded-lg bg-[#F6F8F7] text-[#172421] border border-[#748782]/15 font-medium">
                        {b}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {detail.fishing.method && detail.fishing.method.length > 0 && (
                <div className="text-xs">
                  <span className="font-bold text-[#748782]">经典钓法:</span>
                  <div className="flex flex-wrap gap-1.5 mt-1.5">
                    {detail.fishing.method.map((m, i) => (
                      <span key={i} className="px-2.5 py-1 rounded-lg bg-[#F6F8F7] text-[#172421] border border-[#748782]/15 font-medium">
                        {m}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {detail.fishing.summary && (
                <p className="text-xs text-[#748782] bg-[#F6F8F7] p-3 rounded-2xl leading-relaxed mt-1">
                  💡 {detail.fishing.summary}
                </p>
              )}
            </div>

            {/* Similar species comparison */}
            {detail.similarity && detail.similarity.length > 0 && (
              <div className="bg-white rounded-3xl p-5 shadow-sm border border-[#748782]/10 flex flex-col gap-3">
                <h3 className="text-base font-bold text-[#172421] flex items-center gap-2">
                  <HelpCircle size={18} className="text-[#F29C38]" />
                  易混淆鱼种快速辨析
                </h3>
                {detail.similarity.map((sim, i) => (
                  <div key={i} className="p-3.5 rounded-2xl bg-[#FFF8EC] border border-[#F29C38]/20 text-xs">
                    <div className="font-bold text-[#854D0E] mb-1">
                      与 {sim.similarSpeciesNameCn} 的主要区别：
                    </div>
                    <p className="text-[#854D0E]/90 leading-relaxed">{sim.difference}</p>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* TAB 2: 我的鱼获 */}
        {activeTab === 'CATCHES' && (
          <div className="flex flex-col gap-3">
            {filteredCatches.length > 0 ? (
              filteredCatches.map((catchItem) => (
                <div
                  key={catchItem.id}
                  onClick={() => onSelectCatch(catchItem)}
                  className="bg-white rounded-3xl p-4 shadow-sm border border-[#748782]/10 flex items-center gap-3.5 cursor-pointer hover:border-[#388478]/40 transition-all"
                >
                  <div className="w-16 h-16 rounded-2xl bg-[#EAF4F2] flex items-center justify-center overflow-hidden flex-shrink-0">
                    <FishIllustration size={48} speciesKey={catchItem.speciesId} />
                  </div>
                  <div className="flex-1">
                    <div className="flex items-center justify-between">
                      <h4 className="text-base font-bold text-[#172421]">{catchItem.speciesName}</h4>
                      <span className="text-xs text-[#388478] font-bold font-mono">
                        {catchItem.weightKg} kg
                      </span>
                    </div>
                    <div className="flex items-center gap-3 text-xs text-[#748782] mt-1">
                      <span>{catchItem.lengthCm} cm</span>
                      <span>·</span>
                      <span className="line-clamp-1">{catchItem.location}</span>
                    </div>
                    <span className="text-[10px] text-[#748782]/80 mt-1 block">
                      {catchItem.timeLabel}
                    </span>
                  </div>
                  <ChevronRight size={18} className="text-[#748782]/50" />
                </div>
              ))
            ) : (
              <div className="p-8 text-center bg-white rounded-3xl border border-[#748782]/10">
                <p className="text-xs text-[#748782]">暂无该鱼种的钓获记录</p>
                <button
                  type="button"
                  onClick={onOpenScanner}
                  className="mt-3 px-4 py-2 rounded-full bg-[#388478] text-white text-xs font-semibold"
                >
                  去拍照识别记录
                </button>
              </div>
            )}
          </div>
        )}

        {/* TAB 3: 钓手榜单 */}
        {activeTab === 'RANK' && (
          <div className="bg-white rounded-3xl p-5 shadow-sm border border-[#748782]/10 flex flex-col gap-3">
            <h3 className="text-base font-bold text-[#172421] flex items-center gap-2">
              <Trophy size={18} className="text-[#D6B56D]" />
              {species.nameCn} · 个人与榜单记录
            </h3>

            <div className="p-4 rounded-2xl bg-gradient-to-r from-[#172421] to-[#2B3B36] text-white flex items-center justify-between">
              <div>
                <span className="text-[10px] text-[#D6B56D] font-bold uppercase">个人最佳 (Personal Best)</span>
                <div className="text-2xl font-black mt-1">
                  {filteredCatches.length > 0
                    ? `${Math.max(...filteredCatches.map((c) => c.weightKg))} kg`
                    : '尚未解锁'}
                </div>
                <div className="text-xs text-white/60 mt-0.5">
                  最大体长: {filteredCatches.length > 0 ? `${Math.max(...filteredCatches.map((c) => c.lengthCm))} cm` : '-'}
                </div>
              </div>
              <div className="w-12 h-12 rounded-full bg-white/10 flex items-center justify-center text-[#D6B56D]">
                <Trophy size={24} />
              </div>
            </div>

            <div className="text-xs text-[#748782] leading-relaxed pt-2">
              更多全国钓场排行榜数据会在网络同步后实时刷新。继续作钓提升你的排名！
            </div>
          </div>
        )}
      </main>
    </div>
  );
};
