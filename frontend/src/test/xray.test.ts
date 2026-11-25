import { describe, expect, it, vi } from 'vitest';
import { linkToStory } from './xray';

describe('linkToStory', () => {
  it('annotates the test with the story key as its requirements property', async () => {
    const annotate = vi.fn().mockResolvedValue(undefined);

    await linkToStory('RIA-92')({ annotate });

    expect(annotate).toHaveBeenCalledWith('RIA-92', 'requirements');
  });

  it.each(['', 'RIA', 'ria-92', 'RIA-92 ', 'RIA-92,RIA-91', 'JIRA-1'])(
    'rejects "%s", which is not a single story key',
    (key) => {
      expect(() => linkToStory(key)).toThrow('Not a Jira story key');
    },
  );
});
