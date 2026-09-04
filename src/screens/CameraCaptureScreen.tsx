import React, { useRef, useState, useEffect } from 'react';
import { Camera, Image as ImageIcon, Sparkles, AlertCircle, RefreshCw, Zap } from 'lucide-react';
import { DEMO_FISH_SAMPLES, DemoFishSample } from '../data/fallbackData';
import { FishIllustration } from '../components/FishIllustration';

interface CameraCaptureScreenProps {
  onImageSelected: (image: string | HTMLCanvasElement, hintKey?: string) => void;
}

export const CameraCaptureScreen: React.FC<CameraCaptureScreenProps> = ({
  onImageSelected,
}) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [hasCamera, setHasCamera] = useState<boolean>(false);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [activeFacingMode, setActiveFacingMode] = useState<'environment' | 'user'>('environment');
  const [selectedDemo, setSelectedDemo] = useState<DemoFishSample | null>(null);

  useEffect(() => {
    let stream: MediaStream | null = null;

    async function startCamera() {
      try {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
          setHasCamera(false);
          return;
        }
        stream = await navigator.mediaDevices.getUserMedia({
          video: {
            facingMode: activeFacingMode,
            width: { ideal: 1280 },
            height: { ideal: 720 },
          },
          audio: false,
        });

        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          setHasCamera(true);
          setCameraError(null);
        }
      } catch (err) {
        console.log('Camera not available or permitted, using preview/samples mode', err);
        setHasCamera(false);
        setCameraError('无法访问摄像头或权限被拒绝，您可以直接上传照片或选择下方实战样本体验。');
      }
    }

    startCamera();

    return () => {
      if (stream) {
        stream.getTracks().forEach((track) => track.stop());
      }
    };
  }, [activeFacingMode]);

  const handleCapture = () => {
    if (!videoRef.current || !canvasRef.current) return;
    const video = videoRef.current;
    const canvas = canvasRef.current;
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
    const ctx = canvas.getContext('2d');
    if (ctx) {
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
      onImageSelected(canvas);
    }
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (event) => {
      const result = event.target?.result;
      if (typeof result === 'string') {
        onImageSelected(result);
      }
    };
    reader.readAsDataURL(file);
  };

  const handleDemoSample = (sample: DemoFishSample) => {
    setSelectedDemo(sample);
    // Create an artificial canvas with demo illustration
    const canvas = document.createElement('canvas');
    canvas.width = 640;
    canvas.height = 480;
    const ctx = canvas.getContext('2d');
    if (ctx) {
      // background
      ctx.fillStyle = '#EAF4F2';
      ctx.fillRect(0, 0, 640, 480);
      // subtle water ripple
      ctx.strokeStyle = '#38847822';
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.arc(320, 240, 180, 0, Math.PI * 2);
      ctx.stroke();

      // trigger recognition with hint
      onImageSelected(canvas, sample.speciesKey);
    }
  };

  return (
    <div className="flex flex-col min-h-screen bg-[#172421] text-white">
      {/* Hidden file input */}
      <input
        type="file"
        ref={fileInputRef}
        onChange={handleFileUpload}
        accept="image/*"
        className="hidden"
      />
      <canvas ref={canvasRef} className="hidden" />

      {/* Top Header */}
      <div className="p-4 flex items-center justify-between z-20">
        <div>
          <div className="flex items-center gap-1.5 text-xs text-[#5F9386] font-bold uppercase tracking-wider">
            <Zap size={13} className="text-[#D6B56D]" />
            渔见 AI · 智能镜头
          </div>
          <h1 className="text-xl font-black text-white">对准鱼体拍照识别</h1>
        </div>
        {hasCamera && (
          <button
            type="button"
            onClick={() => setActiveFacingMode((prev) => (prev === 'environment' ? 'user' : 'environment'))}
            className="p-2 rounded-full bg-white/10 text-white backdrop-blur-md hover:bg-white/20"
            aria-label="翻转镜头"
          >
            <RefreshCw size={18} />
          </button>
        )}
      </div>

      {/* Viewfinder Area */}
      <div className="relative flex-1 flex items-center justify-center overflow-hidden bg-black/40 mx-3 rounded-3xl border border-white/10 shadow-2xl">
        {hasCamera ? (
          <video
            ref={videoRef}
            autoPlay
            playsInline
            muted
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="p-6 text-center flex flex-col items-center justify-center max-w-sm">
            <div className="w-20 h-20 rounded-full bg-white/5 border border-white/10 flex items-center justify-center mb-4 text-[#5F9386]">
              <Camera size={36} />
            </div>
            <h2 className="text-lg font-bold text-white mb-2">准备就绪</h2>
            <p className="text-xs text-white/60 leading-relaxed mb-6">
              {cameraError || '点击下方按钮拍摄、相册上传，或者直接点击“实战测试样本”一秒体验 AI 鱼体检测与质量门禁。'}
            </p>
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="px-5 py-2.5 rounded-full bg-[#388478] text-white text-xs font-semibold flex items-center gap-2 hover:bg-[#2E6F65] active:scale-95 transition-transform"
            >
              <ImageIcon size={15} />
              <span>选择手机相册照片</span>
            </button>
          </div>
        )}

        {/* Reticle Viewfinder Target Overlays */}
        <div className="absolute inset-8 pointer-events-none border border-white/20 rounded-2xl flex items-center justify-center">
          {/* 4 corner brackets */}
          <div className="absolute top-0 left-0 w-8 h-8 border-t-2 border-l-2 border-[#388478] rounded-tl-lg" />
          <div className="absolute top-0 right-0 w-8 h-8 border-t-2 border-r-2 border-[#388478] rounded-tr-lg" />
          <div className="absolute bottom-0 left-0 w-8 h-8 border-b-2 border-l-2 border-[#388478] rounded-bl-lg" />
          <div className="absolute bottom-0 right-0 w-8 h-8 border-b-2 border-r-2 border-[#388478] rounded-br-lg" />

          {/* Subtitle guidance */}
          <div className="bg-black/60 backdrop-blur-md px-3 py-1 rounded-full text-[11px] text-white/80 font-medium">
            将整条鱼置于框内 · 避免反光或重度遮挡
          </div>
        </div>
      </div>

      {/* Quick Test Demo Samples Tray */}
      <div className="p-4 z-20">
        <div className="flex items-center justify-between mb-2 px-1">
          <span className="text-xs font-bold text-[#D6B56D] flex items-center gap-1.5">
            <Sparkles size={13} />
            实战测试样本 (一键体验 AI Pipeline)
          </span>
          <span className="text-[10px] text-white/40">点击任意样本</span>
        </div>

        <div className="flex gap-2.5 overflow-x-auto pb-2 scrollbar-none">
          {DEMO_FISH_SAMPLES.map((sample) => (
            <button
              key={sample.id}
              type="button"
              onClick={() => handleDemoSample(sample)}
              className="flex-shrink-0 flex items-center gap-2.5 px-3 py-2 rounded-2xl bg-white/10 hover:bg-white/20 border border-white/10 active:scale-95 transition-all text-left"
            >
              <div
                className="w-10 h-10 rounded-xl flex items-center justify-center"
                style={{ backgroundColor: `${sample.svgColor}33` }}
              >
                <FishIllustration size={32} speciesKey={sample.speciesKey} />
              </div>
              <div className="flex flex-col">
                <span className="text-xs font-bold text-white line-clamp-1">{sample.name}</span>
                <span className="text-[10px] text-[#D6B56D] line-clamp-1">{sample.difficulty}</span>
              </div>
            </button>
          ))}
        </div>
      </div>

      {/* Bottom Shutter & Controls */}
      <div className="p-6 pt-2 flex items-center justify-around z-20">
        {/* Photo Gallery Picker */}
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          className="flex flex-col items-center gap-1 text-white/70 hover:text-white transition-colors"
        >
          <div className="w-12 h-12 rounded-full bg-white/10 flex items-center justify-center hover:bg-white/20">
            <ImageIcon size={20} />
          </div>
          <span className="text-[11px]">相册上传</span>
        </button>

        {/* Shutter Button */}
        <button
          type="button"
          onClick={hasCamera ? handleCapture : () => fileInputRef.current?.click()}
          className="w-18 h-18 rounded-full border-4 border-white flex items-center justify-center p-1 active:scale-90 transition-transform shadow-lg shadow-[#388478]/40"
        >
          <div className="w-full h-full rounded-full bg-[#388478] hover:bg-[#2E6F65] flex items-center justify-center transition-colors">
            <Camera size={26} className="text-white" />
          </div>
        </button>

        {/* Info or Tips */}
        <button
          type="button"
          onClick={() => handleDemoSample(DEMO_FISH_SAMPLES[0])}
          className="flex flex-col items-center gap-1 text-white/70 hover:text-white transition-colors"
        >
          <div className="w-12 h-12 rounded-full bg-white/10 flex items-center justify-center hover:bg-white/20 text-[#D6B56D]">
            <Sparkles size={20} />
          </div>
          <span className="text-[11px]">经典样本</span>
        </button>
      </div>
    </div>
  );
};
