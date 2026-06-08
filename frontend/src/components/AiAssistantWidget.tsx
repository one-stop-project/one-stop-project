import { useState, useRef, useEffect, FormEvent } from 'react';
import { Sparkles, Send, X } from 'lucide-react';
import { useShoppingAssistantMutation } from '@/hooks/queries/useAiQuery';

// ════════════════════════════════════════════════════════════
//  AI 쇼핑 어시스턴트 — 플로팅 채팅 위젯
//  레이아웃 우측 하단에 고정. 클릭 시 채팅창 토글.
//  App.tsx 또는 공통 레이아웃에 <AiAssistantWidget /> 한 줄 추가.
// ════════════════════════════════════════════════════════════

interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
}

export default function AiAssistantWidget() {
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([
    { role: 'assistant', text: '안녕하세요! 무엇을 도와드릴까요? 상품 추천이나 쇼핑 관련 질문을 해보세요.' },
  ]);

  const askMutation = useShoppingAssistantMutation();
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages, askMutation.isPending]);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    const message = input.trim();
    if (!message || askMutation.isPending) return;

    setMessages((prev) => [...prev, { role: 'user', text: message }]);
    setInput('');

    askMutation.mutate(message, {
      onSuccess: (res) => {
        setMessages((prev) => [...prev, { role: 'assistant', text: res.answer }]);
      },
      onError: () => {
        setMessages((prev) => [
          ...prev,
          { role: 'assistant', text: '죄송해요, 답변을 가져오지 못했습니다. 다시 시도해주세요.' },
        ]);
      },
    });
  };

  return (
    <>
      {/* 플로팅 버튼 */}
      {!open && (
        <button
          onClick={() => setOpen(true)}
          className="fixed bottom-6 right-6 z-50 w-14 h-14 rounded-full bg-primary-600 hover:bg-primary-700 text-white shadow-lg flex items-center justify-center transition-colors"
          aria-label="AI 어시스턴트 열기"
        >
          <Sparkles className="w-6 h-6" />
        </button>
      )}

      {/* 채팅창 */}
      {open && (
        <div className="fixed bottom-6 right-6 z-50 w-[360px] max-w-[calc(100vw-3rem)] h-[520px] max-h-[calc(100vh-3rem)] bg-white rounded-2xl shadow-lg border border-gray-200 flex flex-col animate-slide-up">
          {/* 헤더 */}
          <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100 bg-primary-600 rounded-t-2xl">
            <div className="flex items-center gap-2 text-white">
              <Sparkles className="w-5 h-5" />
              <span className="font-semibold">AI 쇼핑 어시스턴트</span>
            </div>
            <button
              onClick={() => setOpen(false)}
              className="text-white/80 hover:text-white"
              aria-label="닫기"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* 메시지 영역 */}
          <div ref={scrollRef} className="flex-1 overflow-y-auto p-4 space-y-3">
            {messages.map((msg, i) => (
              <div
                key={i}
                className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
              >
                <div
                  className={`max-w-[80%] px-3.5 py-2.5 rounded-2xl text-sm whitespace-pre-wrap ${
                    msg.role === 'user'
                      ? 'bg-primary-600 text-white rounded-br-sm'
                      : 'bg-gray-100 text-gray-800 rounded-bl-sm'
                  }`}
                >
                  {msg.text}
                </div>
              </div>
            ))}

            {askMutation.isPending && (
              <div className="flex justify-start">
                <div className="bg-gray-100 text-gray-400 px-3.5 py-2.5 rounded-2xl rounded-bl-sm text-sm">
                  답변 작성 중...
                </div>
              </div>
            )}
          </div>

          {/* 입력 영역 */}
          <form
            onSubmit={handleSubmit}
            className="p-3 border-t border-gray-100 flex items-center gap-2"
          >
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="질문을 입력하세요 (최대 500자)"
              maxLength={500}
              className="flex-1 input-field py-2"
              disabled={askMutation.isPending}
            />
            <button
              type="submit"
              disabled={askMutation.isPending || !input.trim()}
              className="btn-primary p-2.5 shrink-0"
              aria-label="전송"
            >
              <Send className="w-5 h-5" />
            </button>
          </form>
        </div>
      )}
    </>
  );
}
