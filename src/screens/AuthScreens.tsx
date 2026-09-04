import React, { useState } from 'react';
import { UserSession } from '../types';
import { YujianTopBar } from '../components/YujianTopBar';
import { User, Lock, ArrowRight, Compass, Sparkles } from 'lucide-react';

interface AuthScreensProps {
  onSuccess: (session: UserSession) => void;
  onCancel: () => void;
}

export const AuthScreens: React.FC<AuthScreensProps> = ({ onSuccess, onCancel }) => {
  const [isRegister, setIsRegister] = useState(false);
  const [username, setUsername] = useState('angler_master');
  const [nickname, setNickname] = useState('江畔钓客');
  const [password, setPassword] = useState('123456');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const session: UserSession = {
      userId: `user_${Date.now()}`,
      username: username.trim() || 'yujian_user',
      nickname: nickname.trim() || username.trim() || '垂钓高手',
      accessToken: `token_${Math.random().toString(36).substring(2)}`,
      isLoggedIn: true,
    };
    onSuccess(session);
  };

  const handleGuestLogin = () => {
    const session: UserSession = {
      userId: 'guest_angler',
      username: 'guest_user',
      nickname: '游客钓友',
      accessToken: 'guest_token',
      isLoggedIn: true,
    };
    onSuccess(session);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in">
      <div className="w-full max-w-sm bg-white rounded-3xl p-6 shadow-2xl relative">
        <div className="text-center mb-6">
          <div className="w-14 h-14 rounded-2xl bg-[#388478] text-white flex items-center justify-center mx-auto mb-3 shadow-md shadow-[#388478]/30">
            <Compass size={28} />
          </div>
          <h2 className="text-xl font-black text-[#172421]">
            {isRegister ? '注册渔见账号' : '登录渔见 AI'}
          </h2>
          <p className="text-xs text-[#748782] mt-1">同步云端图鉴与个人鱼获战报</p>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          <div>
            <label className="text-xs font-semibold text-[#172421] block mb-1">账号 / 手机号</label>
            <div className="relative">
              <User size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-[#748782]" />
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="请输入用户名"
                className="w-full pl-9 pr-3.5 py-2.5 rounded-xl border border-[#748782]/20 text-xs focus:border-[#388478] outline-none"
                required
              />
            </div>
          </div>

          {isRegister && (
            <div>
              <label className="text-xs font-semibold text-[#172421] block mb-1">钓客昵称</label>
              <div className="relative">
                <Sparkles size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-[#748782]" />
                <input
                  type="text"
                  value={nickname}
                  onChange={(e) => setNickname(e.target.value)}
                  placeholder="例如：千岛湖老钓客"
                  className="w-full pl-9 pr-3.5 py-2.5 rounded-xl border border-[#748782]/20 text-xs focus:border-[#388478] outline-none"
                  required
                />
              </div>
            </div>
          )}

          <div>
            <label className="text-xs font-semibold text-[#172421] block mb-1">密码</label>
            <div className="relative">
              <Lock size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-[#748782]" />
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="请输入登录密码"
                className="w-full pl-9 pr-3.5 py-2.5 rounded-xl border border-[#748782]/20 text-xs focus:border-[#388478] outline-none"
                required
              />
            </div>
          </div>

          <button
            type="submit"
            className="w-full mt-2 py-3 rounded-full bg-[#388478] hover:bg-[#2E6F65] text-white font-bold text-xs flex items-center justify-center gap-1.5 shadow-md shadow-[#388478]/20 active:scale-98 transition-all"
          >
            <span>{isRegister ? '立即注册并登录' : '立即登录'}</span>
            <ArrowRight size={14} />
          </button>
        </form>

        <div className="flex items-center justify-between text-xs text-[#748782] mt-4 pt-4 border-t border-[#748782]/10">
          <button
            type="button"
            onClick={() => setIsRegister(!isRegister)}
            className="hover:text-[#388478] font-medium"
          >
            {isRegister ? '已有账号？去登录' : '没有账号？免费注册'}
          </button>

          <button
            type="button"
            onClick={handleGuestLogin}
            className="hover:text-[#388478] font-medium"
          >
            游客试用
          </button>
        </div>

        <button
          type="button"
          onClick={onCancel}
          className="w-full mt-3 py-2 text-center text-xs text-[#748782] hover:text-[#172421]"
        >
          暂不登录，返回
        </button>
      </div>
    </div>
  );
};
