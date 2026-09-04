import React, { useState } from 'react';
import { FishKnowledgeDetail, FishGuideItem } from '../types';
import { KNOWLEDGE_DETAILS, INITIAL_SPECIES_LIST } from '../data/fallbackData';
import { YujianTopBar } from '../components/YujianTopBar';
import { FiveCardsViewer } from '../components/FiveCardsViewer';
import { FishIllustration } from '../components/FishIllustration';
import {
  Compass,
  Layers,
  Anchor,
  HelpCircle,
  Video,
  CheckCircle2,
  Lock,
  ChevronRight,
  Sparkles,
} from 'lucide-react';

interface SpeciesDetailScreenProps {
  speciesId: string;
  onBack: () => void;
  onOpenScanner: () => void;
  onSelectSimilarSpecies?: (id: string) => void;
}

export const SpeciesDetailScreen: React.FC<SpeciesDetailScreenProps> = ({
  speciesId,
  onBack,
  onOpenScanner,
  onSelectSimilarSpecies,
}) => {
  const [activeTab, setActiveTab] = useState<'CARDS' | 'ECOLOGY' | 'GEAR' | 'SIMILARITY' | 'VIDEO'>('CARDS');

  const knowledge: FishKnowledgeDetail =
    KNOWLEDGE_DETAILS[speciesId] ||
    KNOWLEDGE_DETAILS['crucian_carp'] || {
      species: INITIAL_SPECIES_LIST.find((s) => s.id === speciesId) || INITIAL_SPECIES_LIST[0],
      cards: [],
      gallery: [],
      profile: { features: [], habitat: [], season: [] },
      fishing: { season: [], bait: [], method: [], summary: '' },
      videos: [],
      similarity: [],
      knowledge: {
        ecology: { waterLayer: '中下层', behavior: '群居' },
        gear: { rod: '4.5m', line: '1.5号', hook: '伊豆5号', bait: ['蚯蚓'] },
        skill: { tip: '注意观漂' },
      },
    };

  const species = knowledge.species;

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
            className="px-3 py-1.5 rounded-full bg-[#388478] text-white text-xs font-semibold hover:bg-[#2E6F65] active:scale-95 flex items-center gap-1 shadow-xs"
          >
            <Sparkles size={13} />
            <span>识鱼</span>
          </button>
        }
      />

      {/* Hero Overview Header */}
      <div className="bg-gradient-to-b from-white to-[#F6F8F7] px-5 pt-4 pb-4 border-b border-[#748782]/10">
        <div className="flex items-center justify-between gap-4">
          <div className="flex-1">
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold text-[#388478] bg-[#EAF4F2] px-2.5 py-0.5 rounded-full">
                {species.category}
              </span>
              {species.discovered ? (
                <span className="text-xs font-semibold text-[#388478] flex items-center gap-0.5">
                  <CheckCircle2 size={13} />
                  已点亮图鉴
                </span>
              ) : (
                <span className="text-xs font-semibold text-[#748782] flex items-center gap-0.5">
                  <Lock size={13} />
                  未解锁
                </span>
              )}
            </div>
            <h1 className="text-2xl font-black text-[#172421] mt-1">{species.nameCn}</h1>
            <p className="text-xs text-[#748782] font-mono italic">{species.scientificName}</p>
            {species.aliases.length > 0 && (
              <p className="text-xs text-[#748782] mt-1">
                俗称: <span className="text-[#172421]">{species.aliases.join('、')}</span>
              </p>
            )}
          </div>

          <div className="w-24 h-24 rounded-2xl bg-[#EAF4F2] flex items-center justify-center p-2 flex-shrink-0 shadow-inner">
            <FishIllustration size={80} speciesKey={species.id} />
          </div>
        </div>

        {/* Tab navigation */}
        <div className="flex items-center gap-1.5 overflow-x-auto pt-4 no-scrollbar">
          {[
            { id: 'CARDS', label: '五张鱼鉴卡', icon: Layers },
            { id: 'ECOLOGY', label: '生态习性', icon: Compass },
            { id: 'GEAR', label: '钓组技法', icon: Anchor },
            { id: 'SIMILARITY', label: '相似鱼辨析', icon: HelpCircle },
            { id: 'VIDEO', label: '科普视频', icon: Video },
          ].map((tab) => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id as any)}
                className={`flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-semibold transition-all whitespace-nowrap ${
                  isActive
                    ? 'bg-[#172421] text-white shadow-xs'
                    : 'bg-white text-[#748782] hover:bg-[#EAF4F2] hover:text-[#172421]'
                }`}
              >
                <Icon size={12} />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      <main className="p-4 max-w-lg mx-auto w-full flex flex-col gap-4">
        {/* CARDS TAB */}
        {activeTab === 'CARDS' && (
          <div className="flex flex-col gap-3">
            <div className="text-xs text-[#748782] flex items-center justify-between px-1">
              <span>左右滑动切换卡片（点击卡片翻面看详情）</span>
              <span className="font-mono">{knowledge.cards.length} 张核心鱼鉴</span>
            </div>
            <FiveCardsViewer cards={knowledge.cards} />
          </div>
        )}

        {/* ECOLOGY TAB */}
        {activeTab === 'ECOLOGY' && (
          <div className="flex flex-col gap-3">
            <div className="bg-white rounded-3xl p-5 shadow-sm border border-[#748782]/10 flex flex-col gap-4">
              <h3 className="text-base font-bold text-[#172421] flex items-center gap-2">
                <Compass size={18} className="text-[#388478]" />
                栖息与水层特征
              </h3>
              <div className="grid grid-cols-2 gap-3">
                <div className="bg-[#F6F8F7] p-3.5 rounded-2xl">
                  <span className="text-[10px] text-[#748782] font-medium block">主要活动水层</span>
                  <span className="text-sm font-black text-[#172421] mt-0.5 block">
                    {knowledge.knowledge.ecology.waterLayer || '中下层'}
                  </span>
                </div>
                <div className="bg-[#F6F8F7] p-3.5 rounded-2xl">
                  <span className="text-[10px] text-[#748782] font-medium block">行为摄食习性</span>
                  <span className="text-sm font-black text-[#172421] mt-0.5 block">
                    {knowledge.knowledge.ecology.behavior || '昼伏夜出 / 底栖'}
                  </span>
                </div>
              </div>

              <div>
                <span className="text-xs font-bold text-[#172421] block mb-2">典型体貌特征</span>
                <div className="flex flex-col gap-1.5">
                  {knowledge.profile.features.map((f, idx) => (
                    <div key={idx} className="flex items-start gap-2 text-xs text-[#172421]/90">
                      <span className="w-1.5 h-1.5 rounded-full bg-[#388478] mt-1.5 flex-shrink-0" />
                      <span>{f}</span>
                    </div>
                  ))}
                </div>
              </div>

              <div>
                <span className="text-xs font-bold text-[#172421] block mb-2">栖息水域环境</span>
                <div className="flex flex-wrap gap-1.5">
                  {knowledge.profile.habitat.map((h, idx) => (
                    <span
                      key={idx}
                      className="px-2.5 py-1 bg-[#EAF4F2] text-[#388478] rounded-lg text-xs font-medium"
                    >
                      {h}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* GEAR TAB */}
        {activeTab === 'GEAR' && (
          <div className="flex flex-col gap-3">
            <div className="bg-white rounded-3xl p-5 shadow-sm border border-[#748782]/10 flex flex-col gap-4">
              <h3 className="text-base font-bold text-[#172421] flex items-center gap-2">
                <Anchor size={18} className="text-[#388478]" />
                推荐钓具与饵料线组
              </h3>

              <div className="grid grid-cols-2 gap-3 text-xs">
                <div className="p-3 bg-[#F6F8F7] rounded-2xl">
                  <span className="text-[10px] text-[#748782] block">建议鱼竿</span>
                  <span className="font-bold text-[#172421]">{knowledge.knowledge.gear.rod}</span>
                </div>
                <div className="p-3 bg-[#F6F8F7] rounded-2xl">
                  <span className="text-[10px] text-[#748782] block">线组规格</span>
                  <span className="font-bold text-[#172421]">{knowledge.knowledge.gear.line}</span>
                </div>
                <div className="p-3 bg-[#F6F8F7] rounded-2xl">
                  <span className="text-[10px] text-[#748782] block">推荐鱼钩</span>
                  <span className="font-bold text-[#172421]">{knowledge.knowledge.gear.hook}</span>
                </div>
                <div className="p-3 bg-[#F6F8F7] rounded-2xl">
                  <span className="text-[10px] text-[#748782] block">黄金适钓季节</span>
                  <span className="font-bold text-[#172421]">
                    {knowledge.fishing.season.join('、')}
                  </span>
                </div>
              </div>

              <div>
                <span className="text-xs font-bold text-[#172421] block mb-2">推荐诱饵与味型</span>
                <div className="flex flex-wrap gap-1.5">
                  {knowledge.knowledge.gear.bait.map((b, idx) => (
                    <span
                      key={idx}
                      className="px-2.5 py-1 bg-[#FBE6B6] text-[#172421] rounded-lg text-xs font-semibold"
                    >
                      {b}
                    </span>
                  ))}
                </div>
              </div>

              <div className="p-3.5 bg-[#EAF4F2] rounded-2xl text-xs border border-[#388478]/20">
                <span className="font-bold text-[#388478] block mb-1">实战大师秘诀 (Pro Tip):</span>
                <p className="text-[#172421]/90 leading-relaxed">
                  {knowledge.knowledge.skill.tip || knowledge.fishing.summary}
                </p>
              </div>
            </div>
          </div>
        )}

        {/* SIMILARITY TAB */}
        {activeTab === 'SIMILARITY' && (
          <div className="flex flex-col gap-3">
            <div className="bg-white rounded-3xl p-5 shadow-sm border border-[#748782]/10 flex flex-col gap-4">
              <h3 className="text-base font-bold text-[#172421] flex items-center gap-2">
                <HelpCircle size={18} className="text-[#388478]" />
                相似易混淆鱼种辨析
              </h3>

              {knowledge.similarity && knowledge.similarity.length > 0 ? (
                <div className="flex flex-col gap-3">
                  {knowledge.similarity.map((sim, idx) => (
                    <div
                      key={idx}
                      className="p-3.5 bg-[#F6F8F7] rounded-2xl border border-[#748782]/10 flex flex-col gap-2"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-bold text-sm text-[#172421]">
                          对比：{sim.similarSpeciesNameCn}
                        </span>
                        {onSelectSimilarSpecies && (
                          <button
                            type="button"
                            onClick={() => onSelectSimilarSpecies(sim.similarSpeciesId)}
                            className="text-xs text-[#388478] font-semibold flex items-center gap-0.5 hover:underline"
                          >
                            <span>查看该鱼</span>
                            <ChevronRight size={13} />
                          </button>
                        )}
                      </div>
                      <p className="text-xs text-[#748782] leading-relaxed">
                        <strong className="text-[#172421]">核心差异：</strong>
                        {sim.difference}
                      </p>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-xs text-[#748782]">该鱼种体征较为独特，无易混淆常见鱼种。</p>
              )}
            </div>
          </div>
        )}

        {/* VIDEO TAB */}
        {activeTab === 'VIDEO' && (
          <div className="flex flex-col gap-3">
            <div className="bg-white rounded-3xl p-5 shadow-sm border border-[#748782]/10 flex flex-col gap-4">
              <h3 className="text-base font-bold text-[#172421] flex items-center gap-2">
                <Video size={18} className="text-[#388478]" />
                精选科普与垂钓实战视频
              </h3>

              {knowledge.videos && knowledge.videos.length > 0 ? (
                <div className="flex flex-col gap-3">
                  {knowledge.videos.map((vid) => (
                    <div
                      key={vid.id}
                      className="p-4 bg-[#F6F8F7] rounded-2xl border border-[#748782]/10 flex flex-col gap-2"
                    >
                      <div className="flex items-center justify-between">
                        <h4 className="text-xs font-bold text-[#172421]">{vid.title}</h4>
                        <span className="text-[10px] font-mono bg-[#EAF4F2] text-[#388478] px-2 py-0.5 rounded-md">
                          {Math.floor(vid.duration / 60)}:
                          {(vid.duration % 60).toString().padStart(2, '0')}
                        </span>
                      </div>
                      <div className="flex flex-wrap gap-1">
                        {vid.tags.map((t, idx) => (
                          <span
                            key={idx}
                            className="text-[10px] text-[#748782] bg-white px-2 py-0.5 rounded-full"
                          >
                            #{t}
                          </span>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-xs text-[#748782]">暂无精选视频，敬请期待更新。</p>
              )}
            </div>
          </div>
        )}

        {/* Bottom CTA to scan */}
        <div className="mt-2">
          <button
            type="button"
            onClick={onOpenScanner}
            className="w-full py-3.5 px-4 rounded-full bg-[#388478] text-white font-semibold text-sm flex items-center justify-center gap-2 hover:bg-[#2E6F65] active:scale-[0.98] transition-transform shadow-md shadow-[#388478]/25"
          >
            <Sparkles size={18} />
            <span>智能识鱼 · 记录本次渔获</span>
          </button>
        </div>
      </main>
    </div>
  );
};
