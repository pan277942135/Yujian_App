import React, { useState } from 'react';
import { FishGuideItem } from '../types';
import { FishIllustration } from '../components/FishIllustration';
import { TagChip } from '../components/TagChip';
import { Search, Compass, Camera, Lock, CheckCircle2, ChevronRight } from 'lucide-react';

interface FishGuideHomeScreenProps {
  speciesList: FishGuideItem[];
  onSelectSpecies: (speciesId: string) => void;
  onOpenScanner: () => void;
}

export const FishGuideHomeScreen: React.FC<FishGuideHomeScreenProps> = ({
  speciesList,
  onSelectSpecies,
  onOpenScanner,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [filterMode, setFilterMode] = useState<'ALL' | 'DISCOVERED' | 'LOCKED'>('ALL');

  const discoveredCount = speciesList.filter((s) => s.discovered).length;
  const totalCount = speciesList.length;
  const progressPercent = Math.round((discoveredCount / totalCount) * 100);

  const filteredList = speciesList.filter((sp) => {
    // text query
    const matchText =
      sp.nameCn.includes(searchQuery) ||
      sp.scientificName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      sp.aliases.some((a) => a.includes(searchQuery)) ||
      sp.category.includes(searchQuery);

    if (!matchText) return false;

    if (filterMode === 'DISCOVERED') return sp.discovered;
    if (filterMode === 'LOCKED') return !sp.discovered;
    return true;
  });

  return (
    <div className="flex flex-col min-h-screen bg-[#F6F8F7] pb-24">
      {/* Header */}
      <div className="sticky top-0 z-20 bg-[#F6F8F7]/95 backdrop-blur-md px-5 pt-4 pb-3 border-b border-[#748782]/10">
        <div className="flex items-center justify-between">
          <div>
            <span className="text-xs font-bold text-[#388478] tracking-wider uppercase flex items-center gap-1">
              <Compass size={13} />
              水域百科 · 鱼鉴
            </span>
            <h1 className="text-2xl font-black text-[#172421]">智能鱼类图鉴</h1>
          </div>
          <button
            type="button"
            onClick={onOpenScanner}
            className="w-10 h-10 rounded-full bg-[#388478] text-white flex items-center justify-center hover:bg-[#2E6F65] active:scale-95 shadow-md shadow-[#388478]/20 transition-transform"
            aria-label="打开识别相机"
          >
            <Camera size={20} />
          </button>
        </div>

        {/* Search Bar */}
        <div className="mt-3 relative">
          <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-[#748782]" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="搜索鱼种、学名、别名（如：加州鲈、昂刺鱼...）"
            className="w-full pl-9 pr-4 py-2.5 rounded-2xl bg-white border border-[#748782]/15 text-xs text-[#172421] placeholder:text-[#748782] outline-none focus:border-[#388478] shadow-xs"
          />
        </div>

        {/* Filter Pills */}
        <div className="flex items-center gap-2 mt-3">
          <TagChip
            label="全部鱼类"
            emphasized={filterMode === 'ALL'}
            onClick={() => setFilterMode('ALL')}
          />
          <TagChip
            label={`已解锁 (${discoveredCount})`}
            emphasized={filterMode === 'DISCOVERED'}
            onClick={() => setFilterMode('DISCOVERED')}
          />
          <TagChip
            label={`未解锁 (${totalCount - discoveredCount})`}
            emphasized={filterMode === 'LOCKED'}
            onClick={() => setFilterMode('LOCKED')}
          />
        </div>
      </div>

      <main className="p-4 flex flex-col gap-4 max-w-lg mx-auto w-full">
        {/* Discovery Progress Card */}
        <div className="p-4 rounded-3xl bg-gradient-to-r from-[#172421] to-[#253935] text-white shadow-md flex items-center justify-between border border-[#388478]/20">
          <div className="flex-1 pr-4">
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-xs font-semibold text-white/80">图鉴探索度</span>
              <span className="text-xs font-bold text-[#D6B56D] font-mono">
                {discoveredCount} / {totalCount} 种 ({progressPercent}%)
              </span>
            </div>
            <div className="w-full h-2.5 bg-white/10 rounded-full overflow-hidden">
              <div
                className="h-full bg-gradient-to-r from-[#5F9386] to-[#D6B56D] rounded-full transition-all duration-500"
                style={{ width: `${progressPercent}%` }}
              />
            </div>
            <p className="text-[11px] text-white/60 mt-2">
              拍照或上传鱼体，即可快速完成 AI 鉴定并点亮专属图鉴
            </p>
          </div>

          <div className="w-12 h-12 rounded-2xl bg-white/10 flex items-center justify-center flex-shrink-0 text-[#D6B56D]">
            <Compass size={24} />
          </div>
        </div>

        {/* 2-Column Grid */}
        <div className="grid grid-cols-2 gap-3">
          {filteredList.map((species) => (
            <div
              key={species.id}
              onClick={() => onSelectSpecies(species.id)}
              className={`rounded-3xl p-3.5 flex flex-col justify-between border transition-all duration-200 cursor-pointer shadow-xs active:scale-[0.98] ${
                species.discovered
                  ? 'bg-white border-[#388478]/15 hover:border-[#388478]/40 hover:shadow-md'
                  : 'bg-[#EDEDEB]/70 border-[#748782]/15 opacity-75'
              }`}
            >
              {/* Top thumbnail & status */}
              <div className="relative w-full aspect-square rounded-2xl bg-[#EAF4F2] flex items-center justify-center overflow-hidden mb-2.5">
                <FishIllustration
                  size={100}
                  speciesKey={species.id}
                  className={species.discovered ? '' : 'grayscale opacity-50'}
                />

                {species.discovered ? (
                  <div className="absolute top-2 right-2 bg-[#388478] text-white p-1 rounded-full shadow-xs">
                    <CheckCircle2 size={12} />
                  </div>
                ) : (
                  <div className="absolute top-2 right-2 bg-black/40 text-white/90 p-1 rounded-full">
                    <Lock size={12} />
                  </div>
                )}

                {species.catches > 0 && (
                  <div className="absolute bottom-2 left-2 bg-[#172421]/85 text-[#D6B56D] text-[10px] px-2 py-0.5 rounded-md font-medium">
                    已钓获 {species.catches} 尾
                  </div>
                )}
              </div>

              {/* Text Info */}
              <div>
                <div className="flex items-center justify-between">
                  <span className="text-[10px] text-[#388478] font-semibold bg-[#EAF4F2] px-2 py-0.5 rounded-full">
                    {species.category}
                  </span>
                </div>
                <h3 className="text-base font-bold text-[#172421] mt-1.5">{species.nameCn}</h3>
                <p className="text-[11px] text-[#748782] font-mono italic line-clamp-1">
                  {species.scientificName}
                </p>
                {species.aliases.length > 0 && (
                  <p className="text-[10px] text-[#748782] line-clamp-1 mt-0.5">
                    别名: {species.aliases.join('/')}
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>

        {filteredList.length === 0 && (
          <div className="p-8 text-center text-[#748782] text-xs">
            未找到符合条件的鱼种，建议更换关键词搜索。
          </div>
        )}
      </main>
    </div>
  );
};
