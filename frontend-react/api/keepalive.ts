export const config = { runtime: 'edge' };

export default async function handler() {
  try {
    const res = await fetch('https://football-value-engine.onrender.com/api/status');
    const data = await res.json();
    return new Response(JSON.stringify({ ok: true, ...data }), {
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (e: any) {
    return new Response(JSON.stringify({ ok: false, error: e.message }), {
      status: 502,
      headers: { 'Content-Type': 'application/json' },
    });
  }
}
