import { beforeEach, type TestContext } from 'vitest';

// Links tests to their Jira story in the JUnit report that CI imports into
// Xray (QA Manual 2.3). The Vitest JUnit reporter writes each annotation as a
// <property>, and Xray reads the "requirements" property as the story key.
// Xray only accepts stories as requirements, never tasks or epics.

const STORY_KEY = /^RIA-\d+$/;

type Annotate = TestContext['annotate'];

/** Returns a hook that annotates the running test with the story it verifies. */
export function linkToStory(story: string): (context: { annotate: Annotate }) => Promise<void> {
  if (!STORY_KEY.test(story)) {
    throw new Error(`Not a Jira story key: "${story}" (expected RIA-<number>)`);
  }
  return async ({ annotate }) => {
    await annotate(story, 'requirements');
  };
}

/** Links every test in the enclosing file or describe block to a story, e.g. requirement('RIA-92'). */
export function requirement(story: string): void {
  beforeEach(linkToStory(story));
}
