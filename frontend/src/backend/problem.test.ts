import { describe, expect, it } from 'vitest';
import { NetworkError, ProblemError, isProblem, problemFrom } from './problem';

function jsonResponse(body: unknown, status: number, statusText = ''): Response {
  return new Response(JSON.stringify(body), {
    status,
    statusText,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

describe('ProblemError', () => {
  it('builds its message from the status, title and detail', () => {
    const error = new ProblemError(409, 'Hub full', 'No free slot', []);

    expect(error.message).toBe('409 Hub full: No free slot');
    expect(error.name).toBe('ProblemError');
  });

  it('leaves the detail out of the message when there is none', () => {
    expect(new ProblemError(404, 'Not found', null, []).message).toBe('404 Not found');
  });

  it('lists the names of the refused fields', () => {
    const error = new ProblemError(400, 'Invalid', null, [
      { name: 'email', reason: 'must be an email' },
      { name: 'stayCode', reason: null },
    ]);

    expect(error.fields).toEqual(['email', 'stayCode']);
  });
});

describe('NetworkError', () => {
  it('says the backend could not be reached', () => {
    const error = new NetworkError();

    expect(error.name).toBe('NetworkError');
    expect(error.message).toBe('The backend could not be reached');
  });
});

describe('problemFrom', () => {
  it('reads the title, detail and invalid params of a problem body', async () => {
    const response = jsonResponse(
      {
        title: 'Invalid sign-up',
        detail: 'Some fields are wrong',
        'invalid-params': [
          { name: 'email', reason: 'must be an email' },
          { name: 'password', reason: '  ' },
          { name: 42 },
          null,
          'stayCode',
        ],
      },
      400,
    );

    const problem = await problemFrom(response);

    expect(problem.status).toBe(400);
    expect(problem.title).toBe('Invalid sign-up');
    expect(problem.detail).toBe('Some fields are wrong');
    expect(problem.invalidParams).toEqual([
      { name: 'email', reason: 'must be an email' },
      { name: 'password', reason: null },
    ]);
  });

  it('falls back to the status text when the body is not JSON', async () => {
    const response = new Response('<html>Bad gateway</html>', {
      status: 502,
      statusText: 'Bad Gateway',
    });

    const problem = await problemFrom(response);

    expect(problem.status).toBe(502);
    expect(problem.title).toBe('Bad Gateway');
    expect(problem.detail).toBeNull();
    expect(problem.invalidParams).toEqual([]);
  });

  it('ignores a blank title and a non-array invalid-params', async () => {
    const response = jsonResponse({ title: '   ', 'invalid-params': 'nope' }, 503, 'Unavailable');

    const problem = await problemFrom(response);

    expect(problem.title).toBe('Unavailable');
    expect(problem.invalidParams).toEqual([]);
  });

  it('falls back to "Error" when there is neither a title nor a status text', async () => {
    const problem = await problemFrom(jsonResponse({}, 500));

    expect(problem.title).toBe('Error');
  });

  it('treats a JSON body that is not an object as having no fields', async () => {
    const problem = await problemFrom(jsonResponse('oops', 500, 'Server Error'));

    expect(problem.title).toBe('Server Error');
    expect(problem.detail).toBeNull();
  });
});

describe('isProblem', () => {
  const problem = new ProblemError(409, 'Conflict', null, []);

  it('recognizes a problem of any status', () => {
    expect(isProblem(problem)).toBe(true);
  });

  it('matches only the given status', () => {
    expect(isProblem(problem, 409)).toBe(true);
    expect(isProblem(problem, 404)).toBe(false);
  });

  it('rejects other errors', () => {
    expect(isProblem(new NetworkError())).toBe(false);
    expect(isProblem('409')).toBe(false);
  });
});
