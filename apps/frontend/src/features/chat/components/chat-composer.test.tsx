import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import enChat from '@/locales/en/chat.json';
import { renderWithProviders } from '@/test/render';
import { ChatComposer } from './chat-composer';

/** §7.3 in isolation: the keyboard contract, and the Send/Stop swap. */

describe('ChatComposer', () => {
  it('sends on Enter but not on Shift+Enter', async () => {
    const onSend = vi.fn();
    const user = userEvent.setup();

    renderWithProviders(<ChatComposer isStreaming={false} onSend={onSend} onStop={vi.fn()} />);
    const field = screen.getByLabelText(enChat.composer.label);

    await user.type(field, 'line one{Shift>}{Enter}{/Shift}line two');
    expect(onSend).not.toHaveBeenCalled();

    await user.type(field, '{Enter}');
    expect(onSend).toHaveBeenCalledWith('line one\nline two');
  });

  it('clears the draft once the message has been handed over', async () => {
    const user = userEvent.setup();

    renderWithProviders(<ChatComposer isStreaming={false} onSend={vi.fn()} onStop={vi.fn()} />);
    const field = screen.getByLabelText(enChat.composer.label);
    await user.type(field, 'Plan Japan{Enter}');

    expect(field).toHaveValue('');
  });

  it('will not send whitespace', async () => {
    const onSend = vi.fn();
    const user = userEvent.setup();

    renderWithProviders(<ChatComposer isStreaming={false} onSend={onSend} onStop={vi.fn()} />);
    await user.type(screen.getByLabelText(enChat.composer.label), '   {Enter}');

    expect(onSend).not.toHaveBeenCalled();
  });

  it('offers Stop in place of Send while a turn is streaming, and keeps the draft', async () => {
    const onSend = vi.fn();
    const onStop = vi.fn();
    const user = userEvent.setup();

    renderWithProviders(<ChatComposer isStreaming onSend={onSend} onStop={onStop} />);
    const field = screen.getByLabelText(enChat.composer.label);
    await user.type(field, 'a follow-up{Enter}');

    // Enter must not queue a second turn while one is running.
    expect(onSend).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: enChat.composer.send })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: enChat.composer.stop }));

    expect(onStop).toHaveBeenCalledTimes(1);
    // §7.3: "the user can stop generation without losing prior content".
    expect(field).toHaveValue('a follow-up');
  });
});
