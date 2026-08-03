/**
 * Chat (PLAN §3.2, ADR 007, task 20).
 *
 * The public surface of the feature. Routes and other features compose these and nothing else —
 * the reducer, the frame decoder and the individual bubbles are internals, and `eslint.config.mjs`
 * enforces that by refusing an import of `@/features/chat/<file>`.
 *
 * The transport itself lives in `lib/api/chat-stream.ts` (ADR 007's documented raw-`fetch`
 * exception) rather than here, so the "only lib/api talks HTTP" rule survives chat.
 */
export { ChatPanel, type ChatPanelProps } from './components/chat-panel';
export { ChatComposer, type ChatComposerProps } from './components/chat-composer';
export { ChatMessageList, type ChatMessageListProps } from './components/chat-message-list';
export { ChatMessageItem, type ChatMessageItemProps } from './components/chat-message-item';
export { ChatStreamingIndicator, type ChatStreamingIndicatorProps } from './components/chat-streaming-indicator';
export { PlannerHomePanel } from './components/planner-home-panel';
export { TripChatPanel, type TripChatPanelProps } from './components/trip-chat-panel';
export { SuggestedPrompts, type SuggestedPromptsProps } from './components/suggested-prompts';
export { useChatStream, type ChatStreamController, type UseChatStreamOptions } from './hooks/use-chat-stream';
export {
  chatReducer,
  initialChatState,
  isStreaming,
  type ChatAction,
  type ChatConnectionState,
  type ChatMessage,
  type ChatMessageStatus,
  type ChatState,
  type ChatToolActivity,
} from './lib/chat-state';
export { newClientMessageId } from './lib/client-message-id';
