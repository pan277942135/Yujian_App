import React from 'react';

interface FishIllustrationProps {
  className?: string;
  size?: number | string;
  bodyColor?: string;
  speciesKey?: string;
}

const SPECIES_COLOR_MAP: Record<string, { body: string; accent: string }> = {
  yellow_catfish: { body: '#D6A838', accent: '#B28522' },
  largemouth_bass: { body: '#4A7C59', accent: '#33583E' },
  crucian_carp: { body: '#6B8E85', accent: '#4E6A63' },
  snakehead: { body: '#3A4E47', accent: '#26342F' },
  grass_carp: { body: '#7A8C53', accent: '#586738' },
  common_carp: { body: '#C06B3E', accent: '#974E28' },
  bighead_carp: { body: '#506370', accent: '#374650' },
  silver_carp: { body: '#8CA4B0', accent: '#697E89' },
  black_carp: { body: '#2E353B', accent: '#1C2226' },
  sharpbelly: { body: '#95B8B1', accent: '#73958E' },
};

export const FishIllustration: React.FC<FishIllustrationProps> = ({
  className = '',
  size = 132,
  bodyColor,
  speciesKey,
}) => {
  const colors = (speciesKey && SPECIES_COLOR_MAP[speciesKey]) || {
    body: bodyColor || '#388478',
    accent: '#2E6F65',
  };

  const finalBodyColor = bodyColor || colors.body;

  return (
    <div
      className={`inline-flex items-center justify-center relative select-none ${className}`}
      style={{ width: size, height: size }}
    >
      <svg
        viewBox="0 0 160 160"
        className="w-full h-full drop-shadow-sm transition-transform duration-300 hover:scale-105"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        {/* Tail fin */}
        <path
          d="M 40 81 L 10 54 L 10 110 Z"
          fill={finalBodyColor}
          opacity="0.95"
        />
        <path
          d="M 10 54 Q 28 81 10 110"
          stroke={colors.accent}
          strokeWidth="2"
          opacity="0.4"
          fill="none"
        />

        {/* Dorsal fin */}
        <path
          d="M 80 56 L 94 33 L 107 56 Z"
          fill={finalBodyColor}
          opacity="0.85"
        />

        {/* Ventral / Anal fin */}
        <path
          d="M 68 104 L 78 120 L 92 104 Z"
          fill={finalBodyColor}
          opacity="0.75"
        />

        {/* Main Fish Body (Tilted ellipse) */}
        <g transform="rotate(-2 85 81)">
          <ellipse
            cx="85"
            cy="81"
            rx="48"
            ry="27"
            fill={finalBodyColor}
          />
          {/* Subtle belly shine highlight */}
          <ellipse
            cx="85"
            cy="92"
            rx="34"
            ry="10"
            fill="#FFFFFF"
            opacity="0.22"
          />
          {/* Subtle back shading */}
          <path
            d="M 45 74 Q 85 55 125 74"
            stroke={colors.accent}
            strokeWidth="3"
            opacity="0.3"
            fill="none"
          />
          {/* Pectoral fin */}
          <path
            d="M 100 84 C 95 95, 84 98, 80 95 C 84 88, 92 82, 100 84 Z"
            fill={colors.accent}
            opacity="0.7"
          />
        </g>

        {/* Eye */}
        <circle cx="118" cy="74" r="5.5" fill="#172421" />
        <circle cx="120" cy="72" r="1.8" fill="#FFFFFF" />

        {/* Whisker barbels for catfish / carp */}
        {(speciesKey === 'yellow_catfish' || speciesKey === 'common_carp') && (
          <>
            <path
              d="M 126 80 Q 138 88 144 84"
              stroke="#B28522"
              strokeWidth="2"
              strokeLinecap="round"
              fill="none"
            />
            <path
              d="M 124 84 Q 134 94 139 96"
              stroke="#B28522"
              strokeWidth="1.5"
              strokeLinecap="round"
              fill="none"
            />
          </>
        )}
      </svg>
    </div>
  );
};
