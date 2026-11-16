// Provider smoke test for the Foza gateway. NO CREDENTIAL IN SOURCE.
//
// The previous version of this file contained a hard-coded API key (P0 finding of the v1.0.12 review). It was removed from the
// working tree in v1.0.13; the old blob still exists in git history, so that key MUST be revoked by its owner and never reused.
//
// Usage (PowerShell):   $env:FOZA_API_KEY = "<your QA key>"; npx tsx ai_test/test_foza_ai.ts
// Never commit the key, never paste it into chat, logs or evidence.
import OpenAI from 'openai';

const apiKey = process.env.FOZA_API_KEY;
if (!apiKey) {
  throw new Error('Set FOZA_API_KEY in the environment (the key must never be committed).');
}

const client = new OpenAI({
  apiKey,
  baseURL: 'https://api.foza.ai/v1',
});

const response = await client.chat.completions.create({
  model: process.env.FOZA_MODEL ?? 'cinb/gemini-3.8-flash',
  messages: [{ role: 'user', content: 'Hello!' }],
});

console.log(response.choices[0].message.content);
