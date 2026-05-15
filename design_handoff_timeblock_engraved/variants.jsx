/* Block state variants for Timebox redesign.
   Each variant renders the same lane scaffolding with its own block treatment
   across four states: empty/draft, created-resting, selected, press-hold drag.

   RESIZE BANDS: every created/draft block carries a top + bottom band that is the
   grab affordance for adjusting start / end time. The bands are part of the block
   visual language — each variant gives them a distinct treatment. They disappear
   only while the whole block is being dragged (move, not resize).
*/

const LANE_HOUR_PX = 96;
const LANE_START_H = 8;
const LANE_END_H = 12;
const LANE_HEIGHT = (LANE_END_H - LANE_START_H) * LANE_HOUR_PX;

const BAND_H = 8; // resize-band height (inside block)

const STATE_ROWS = [
  { id: 'draft',    n: '01', label: 'EMPTY DRAFT',     sub: 'unclaimed slot',                  top: 0,   start: 8*60,    end: 8*60+45 },
  { id: 'resting',  n: '02', label: 'CREATED · IDLE',  sub: 'bands grip start / end',          top: 100, start: 9*60,    end: 9*60+45 },
  { id: 'selected', n: '03', label: 'SELECTED',        sub: 'bands engaged · inspector open',  top: 200, start: 10*60,   end: 11*60 },
  { id: 'drag',     n: '04', label: 'PRESS · HOLD',    sub: 'bands hide · whole block moves',  top: 320, start: 11*60+15,end: 12*60 },
];

function minutesToPx(min) {
  return ((min - LANE_START_H * 60) / 60) * LANE_HOUR_PX;
}

function blockBox(start, end) {
  return {
    position: 'absolute',
    top: minutesToPx(start),
    left: 6,
    right: 6,
    height: minutesToPx(end) - minutesToPx(start),
  };
}

function HourRail({ tone = '#a8a29e' }) {
  const hours = [];
  for (let h = LANE_START_H; h <= LANE_END_H; h++) hours.push(h);
  return (
    <div style={{ position: 'absolute', inset: 0 }}>
      {hours.map(h => {
        const display = h === 12 ? '12 PM' : h > 12 ? `${h - 12} PM` : `${h} AM`;
        return (
          <div
            key={h}
            style={{
              position: 'absolute',
              top: (h - LANE_START_H) * LANE_HOUR_PX - 6,
              right: 10,
              fontFamily: 'JetBrains Mono, monospace',
              fontSize: 10,
              color: tone,
              letterSpacing: '0.04em',
            }}
          >
            {display}
          </div>
        );
      })}
    </div>
  );
}

function GridLines({ tone = '#ece8dc' }) {
  const lines = [];
  for (let h = LANE_START_H; h <= LANE_END_H; h++) {
    lines.push(
      <div
        key={h}
        style={{
          position: 'absolute',
          top: (h - LANE_START_H) * LANE_HOUR_PX,
          left: 0,
          right: 0,
          borderTop: `1px solid ${tone}`,
        }}
      />
    );
  }
  for (let h = LANE_START_H; h < LANE_END_H; h++) {
    lines.push(
      <div
        key={`half-${h}`}
        style={{
          position: 'absolute',
          top: (h - LANE_START_H) * LANE_HOUR_PX + LANE_HOUR_PX / 2,
          left: 0,
          right: 0,
          borderTop: `1px dotted ${tone}`,
          opacity: 0.55,
        }}
      />
    );
  }
  return <>{lines}</>;
}

function Annotations({ ink = '#1f2426', mute = '#a8a29e' }) {
  return (
    <div style={{ width: 168, flex: '0 0 auto', position: 'relative', height: LANE_HEIGHT }}>
      {STATE_ROWS.map(s => (
        <div
          key={s.id}
          style={{
            position: 'absolute',
            top: minutesToPx(s.start) + 2,
            left: 0,
            right: 0,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
            <span style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 10.5, color: mute, letterSpacing: '0.08em' }}>
              {s.n}
            </span>
            <span style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 10.5, color: ink, letterSpacing: '0.1em', fontWeight: 500 }}>
              {s.label}
            </span>
          </div>
          <div style={{ fontSize: 10.5, color: mute, marginTop: 3, fontFamily: 'Inter, sans-serif', lineHeight: 1.35 }}>
            {s.sub}
          </div>
        </div>
      ))}
    </div>
  );
}

function ArtboardShell({ kicker, title, footer, accent = '#1967d2', children }) {
  return (
    <div
      style={{
        width: '100%',
        height: '100%',
        background: '#fff',
        padding: '26px 32px 22px',
        display: 'flex',
        flexDirection: 'column',
        fontFamily: 'Inter, sans-serif',
        color: '#1f2426',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', paddingBottom: 14, marginBottom: 16, borderBottom: '1px solid #ebe7dd' }}>
        <div>
          <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 10, textTransform: 'uppercase', letterSpacing: '0.14em', color: '#a8a29e', marginBottom: 6 }}>
            {kicker}
          </div>
          <div style={{ fontFamily: 'Newsreader, serif', fontSize: 24, fontWeight: 400, letterSpacing: '-0.01em', lineHeight: 1.1 }}>
            {title}
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 10, color: '#a8a29e', letterSpacing: '0.08em', textTransform: 'uppercase' }}>
            planned
          </span>
          <i style={{ width: 8, height: 8, borderRadius: 2, background: accent, display: 'block' }} />
        </div>
      </div>

      <div style={{ display: 'flex', gap: 18, flex: 1, minHeight: 0 }}>{children}</div>

      {footer && (
        <div style={{ marginTop: 14, paddingTop: 12, borderTop: '1px dashed #ebe7dd', fontFamily: 'JetBrains Mono, monospace', fontSize: 10, letterSpacing: '0.06em', color: '#a8a29e', textTransform: 'uppercase', display: 'flex', justifyContent: 'space-between', gap: 12 }}>
          <span>{footer}</span>
          <span>Timebox · block atom</span>
        </div>
      )}
    </div>
  );
}

function Lane({ tint = '#fbfaf7', railTone, gridTone, children }) {
  return (
    <>
      <div style={{ width: 56, position: 'relative', flex: '0 0 auto' }}>
        <HourRail tone={railTone || '#a8a29e'} />
      </div>
      <div
        style={{
          position: 'relative',
          flex: 1,
          minWidth: 220,
          background: tint,
          border: '1px solid #ebe7dd',
          borderRadius: 8,
          height: LANE_HEIGHT,
          overflow: 'visible',
        }}
      >
        <GridLines tone={gridTone || '#ece8dc'} />
        {children}
      </div>
      <Annotations />
    </>
  );
}

/* ─────────── Resize-band primitive (each variant supplies its own paint) ───────── */
function GripDots({ count = 18, color, opacity = 1, height = 1.5 }) {
  // a row of tiny dots, like a thumb-grip texture
  const arr = Array.from({ length: count });
  return (
    <div style={{ display: 'flex', gap: 2, alignItems: 'center', opacity }}>
      {arr.map((_, i) => (
        <i key={i} style={{ width: height, height, borderRadius: '50%', background: color, display: 'block' }} />
      ))}
    </div>
  );
}

function Band({ position = 'top', style, children, label }) {
  return (
    <div
      style={{
        position: 'absolute',
        left: 0,
        right: 0,
        height: BAND_H,
        [position]: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        cursor: 'ns-resize',
        pointerEvents: 'none',
        ...style,
      }}
      aria-label={label}
    >
      {children}
    </div>
  );
}

function Bracket({ corner, size = 10, weight = 1.5, color = '#1f2426', inset = -5 }) {
  const map = [
    { top: inset, left: inset, borderTop: `${weight}px solid ${color}`, borderLeft: `${weight}px solid ${color}` },
    { top: inset, right: inset, borderTop: `${weight}px solid ${color}`, borderRight: `${weight}px solid ${color}` },
    { bottom: inset, right: inset, borderBottom: `${weight}px solid ${color}`, borderRight: `${weight}px solid ${color}` },
    { bottom: inset, left: inset, borderBottom: `${weight}px solid ${color}`, borderLeft: `${weight}px solid ${color}` },
  ];
  return <div style={{ position: 'absolute', width: size, height: size, ...map[corner] }} />;
}

/* ──────────────────────────────────────────────────────────
   VARIANT 1 · Monastic Paper — hairline, ink, mono
   Resize bands: tiny dotted grip-row, ink at full opacity on select.
   ────────────────────────────────────────────────────────── */
function VariantMonastic() {
  const ink = '#1f2426';
  const mute = '#9aa1a3';
  const rule = '#d8d3c8';

  const bandRest = { background: 'rgba(20,20,20,0.025)', borderTop: `1px solid ${rule}`, borderBottom: `1px solid ${rule}` };
  const bandSel  = { background: 'rgba(20,20,20,0.06)',  borderTop: `1px solid ${ink}`,   borderBottom: `1px solid ${ink}`  };
  const bandDraft = { background: 'transparent', borderTop: `1px dashed ${rule}`, borderBottom: `1px dashed ${rule}` };

  const bodyPad = { padding: `${BAND_H + 4}px 12px ${BAND_H + 4}px` };

  return (
    <ArtboardShell
      kicker="01 / paper · hairline · ink"
      title="Monastic"
      footer="Bands · hairline rule with mono-dot grip · ink on select"
    >
      <Lane tint="#fbfaf7" gridTone="#ece8dc">
        {/* DRAFT */}
        <div style={blockBox(STATE_ROWS[0].start, STATE_ROWS[0].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%', border: `1px dashed ${rule}`, borderRadius: 6, background: 'rgba(255,255,255,0.4)' }}>
            <Band position="top" style={bandDraft}><GripDots count={12} color="#b8b0a0" /></Band>
            <Band position="bottom" style={bandDraft}><GripDots count={12} color="#b8b0a0" /></Band>
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, display: 'flex', alignItems: 'center', padding: '0 12px', gap: 10, color: '#b8b0a0', fontFamily: 'JetBrains Mono, monospace' }}>
              <span style={{ fontSize: 14 }}>+</span>
              <span style={{ fontSize: 11.5, letterSpacing: '0.04em' }}>name this slot</span>
              <span style={{ marginLeft: 'auto', fontSize: 10.5, color: '#c2bbac' }}>08:00–08:45</span>
            </div>
          </div>
        </div>

        {/* RESTING */}
        <div style={blockBox(STATE_ROWS[1].start, STATE_ROWS[1].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%', border: `1px solid ${rule}`, borderRadius: 6, background: '#ffffff' }}>
            <Band position="top" style={bandRest}><GripDots count={14} color={mute} /></Band>
            <Band position="bottom" style={bandRest}><GripDots count={14} color={mute} /></Band>
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, ...bodyPad, padding: '0 12px', display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 3 }}>
              <div style={{ fontSize: 12.5, color: ink, fontWeight: 500 }}>Deep work · draft</div>
              <div style={{ fontSize: 10.5, color: mute, fontFamily: 'JetBrains Mono, monospace' }}>09:00 — 09:45</div>
            </div>
          </div>
        </div>

        {/* SELECTED */}
        <div style={blockBox(STATE_ROWS[2].start, STATE_ROWS[2].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%', border: `1.5px solid ${ink}`, borderRadius: 6, background: '#fff', boxShadow: '0 1px 0 rgba(20,20,20,0.04)' }}>
            <Bracket corner={0} color={ink} />
            <Bracket corner={1} color={ink} />
            <Bracket corner={2} color={ink} />
            <Bracket corner={3} color={ink} />
            <Band position="top" style={bandSel}><GripDots count={16} color={ink} /></Band>
            <Band position="bottom" style={bandSel}><GripDots count={16} color={ink} /></Band>
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, padding: '0 14px', display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 3 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
                <div style={{ fontSize: 13, color: ink, fontWeight: 600 }}>Standup</div>
                <div style={{ fontSize: 9.5, color: mute, textTransform: 'uppercase', letterSpacing: '0.1em', fontFamily: 'JetBrains Mono, monospace' }}>editing</div>
              </div>
              <div style={{ fontSize: 10.5, color: '#5a6061', fontFamily: 'JetBrains Mono, monospace' }}>10:00 — 11:00 · 1h</div>
            </div>
          </div>
        </div>

        {/* DRAG */}
        <div style={blockBox(STATE_ROWS[3].start, STATE_ROWS[3].end)}>
          <div style={{ position: 'absolute', inset: 0, border: `1px dashed ${rule}`, borderRadius: 6, background: 'rgba(255,255,255,0.4)' }} />
          <div style={{ position: 'absolute', inset: 0, transform: 'translate(10px,-10px)', border: `1px solid ${ink}`, borderRadius: 6, background: '#fbfaf7', padding: '10px 14px', boxShadow: '0 18px 36px rgba(20,20,20,0.14), 0 2px 6px rgba(20,20,20,0.08)' }}>
            <div style={{ fontSize: 12.5, color: ink, fontWeight: 500 }}>Letter to S.</div>
            <div style={{ fontSize: 10.5, color: mute, fontFamily: 'JetBrains Mono, monospace', marginTop: 3 }}>11:15 — 12:00</div>
          </div>
        </div>
      </Lane>
    </ArtboardShell>
  );
}

/* ──────────────────────────────────────────────────────────
   VARIANT 2 · Editorial Index — serif + mono numerals
   Resize bands: thin double rule (footnote separator).
   ────────────────────────────────────────────────────────── */
function VariantEditorial() {
  const ink = '#1a1a1a';
  const mute = '#9a958c';
  const rule = '#d3cec1';

  function DoubleRuleBand({ tone, position }) {
    return (
      <Band position={position} style={{ background: 'transparent' }}>
        <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 2 }}>
          <div style={{ height: 1, background: tone }} />
          <div style={{ height: 1, background: tone, opacity: 0.55 }} />
        </div>
      </Band>
    );
  }

  function MonoTimeStack({ start, end, faded }) {
    return (
      <div style={{ width: 54, flex: '0 0 auto', display: 'flex', flexDirection: 'column', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div style={{ fontFamily: 'IBM Plex Mono, monospace', fontSize: 11.5, color: faded ? mute : ink, letterSpacing: '0.02em' }}>{start}</div>
        <div style={{ width: 18, height: 1, background: rule, margin: '4px 0' }} />
        <div style={{ fontFamily: 'IBM Plex Mono, monospace', fontSize: 11.5, color: mute, letterSpacing: '0.02em' }}>{end}</div>
      </div>
    );
  }

  return (
    <ArtboardShell
      kicker="02 / serif · mono · index"
      title="Editorial"
      footer="Bands · double hairline rule, ink-black on select"
    >
      <Lane tint="#fbf9f4" gridTone="#ede8d8">
        {/* DRAFT */}
        <div style={blockBox(STATE_ROWS[0].start, STATE_ROWS[0].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%' }}>
            <DoubleRuleBand position="top" tone={rule} />
            <DoubleRuleBand position="bottom" tone={rule} />
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, display: 'flex', alignItems: 'center', gap: 16, padding: '0 12px' }}>
              <MonoTimeStack start="08:00" end="08:45" faded />
              <div style={{ fontFamily: 'Newsreader, serif', fontStyle: 'italic', fontSize: 15, color: mute }}>untitled —</div>
            </div>
          </div>
        </div>

        {/* RESTING */}
        <div style={blockBox(STATE_ROWS[1].start, STATE_ROWS[1].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%' }}>
            <DoubleRuleBand position="top" tone={rule} />
            <DoubleRuleBand position="bottom" tone={rule} />
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, display: 'flex', alignItems: 'center', gap: 16, padding: '0 12px' }}>
              <MonoTimeStack start="09:00" end="09:45" />
              <div style={{ minWidth: 0, flex: 1 }}>
                <div style={{ fontFamily: 'Newsreader, serif', fontSize: 16.5, color: ink, lineHeight: 1.15 }}>Deep work, ch. iv</div>
                <div style={{ fontSize: 11, color: mute, marginTop: 2 }}>writing · 45m</div>
              </div>
              <div style={{ fontFamily: 'IBM Plex Mono, monospace', fontSize: 10, color: mute, letterSpacing: '0.1em' }}>ii</div>
            </div>
          </div>
        </div>

        {/* SELECTED */}
        <div style={blockBox(STATE_ROWS[2].start, STATE_ROWS[2].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%', background: '#fff', border: `1px solid ${ink}` }}>
            <Bracket corner={0} color={ink} size={12} weight={2} inset={-6} />
            <Bracket corner={1} color={ink} size={12} weight={2} inset={-6} />
            <Bracket corner={2} color={ink} size={12} weight={2} inset={-6} />
            <Bracket corner={3} color={ink} size={12} weight={2} inset={-6} />
            <DoubleRuleBand position="top" tone={ink} />
            <DoubleRuleBand position="bottom" tone={ink} />
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, display: 'flex', alignItems: 'center', gap: 16, padding: '0 14px' }}>
              <MonoTimeStack start="10:00" end="11:00" />
              <div style={{ minWidth: 0, flex: 1 }}>
                <div style={{ fontFamily: 'Newsreader, serif', fontSize: 17, color: ink, lineHeight: 1.15, fontWeight: 500 }}>Standup</div>
                <div style={{ fontSize: 11, color: mute, marginTop: 3 }}>team · 1h · in inspector</div>
              </div>
              <div style={{ fontFamily: 'IBM Plex Mono, monospace', fontSize: 10, color: ink, letterSpacing: '0.1em' }}>iii</div>
            </div>
          </div>
        </div>

        {/* DRAG */}
        <div style={blockBox(STATE_ROWS[3].start, STATE_ROWS[3].end)}>
          <div style={{ position: 'absolute', inset: 0 }}>
            <DoubleRuleBand position="top" tone={rule} />
            <DoubleRuleBand position="bottom" tone={rule} />
          </div>
          <div style={{ position: 'absolute', inset: 0, transform: 'translate(10px,-12px) rotate(-1deg)', background: '#fffefa', border: `1px solid ${ink}`, display: 'flex', alignItems: 'center', gap: 16, padding: '0 14px', boxShadow: '0 18px 36px rgba(40,30,20,0.12), 0 2px 6px rgba(40,30,20,0.08)' }}>
            <MonoTimeStack start="11:15" end="12:00" />
            <div style={{ minWidth: 0, flex: 1 }}>
              <div style={{ fontFamily: 'Newsreader, serif', fontSize: 16.5, color: ink, lineHeight: 1.15 }}>Letter to S.</div>
              <div style={{ fontSize: 11, color: mute, marginTop: 2 }}>lifted · 45m</div>
            </div>
            <div style={{ fontFamily: 'IBM Plex Mono, monospace', fontSize: 10, color: mute, letterSpacing: '0.1em' }}>iv</div>
          </div>
        </div>
      </Lane>
    </ArtboardShell>
  );
}

/* ──────────────────────────────────────────────────────────
   VARIANT 3 · Crisp — productivity sharp, accent-led
   Resize bands: chevron-tipped accent strips top/bottom.
   ────────────────────────────────────────────────────────── */
function VariantCrisp() {
  const accent = '#3b6ab8';
  const accentDeep = '#1f4f96';
  const tint = '#eef3fb';
  const tintDeeper = '#dde7f5';
  const ink = '#1a2127';
  const mute = '#6c757d';

  function CrispBand({ position, tone, alpha = 1, dashed = false }) {
    return (
      <Band position={position} style={{ background: tone, opacity: alpha, ...(dashed ? { background: 'transparent', borderTop: `1.5px dashed ${tone}`, borderBottom: position === 'bottom' ? 'none' : undefined } : {}) }}>
        <div style={{ display: 'flex', gap: 2, alignItems: 'center' }}>
          <i style={{ width: 18, height: 2, borderRadius: 1, background: position === 'top' ? '#fff' : '#fff', opacity: 0.55, display: 'block' }} />
          <i style={{ width: 8, height: 2, borderRadius: 1, background: '#fff', opacity: 0.4, display: 'block' }} />
        </div>
      </Band>
    );
  }

  return (
    <ArtboardShell
      kicker="03 / accent · ringed · sharp"
      title="Crisp"
      footer="Bands · solid accent strips · grip pills inside"
      accent={accent}
    >
      <Lane tint="#f4f7fc" gridTone="#e2e8f1">
        {/* DRAFT */}
        <div style={blockBox(STATE_ROWS[0].start, STATE_ROWS[0].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%', border: `1.5px dashed ${accent}`, borderRadius: 4, background: 'rgba(255,255,255,0.55)' }}>
            <Band position="top" style={{ background: `${accent}33` }}>
              <div style={{ display: 'flex', gap: 2 }}>
                <i style={{ width: 14, height: 2, borderRadius: 1, background: accent, opacity: 0.55 }} />
                <i style={{ width: 6, height: 2, borderRadius: 1, background: accent, opacity: 0.4 }} />
              </div>
            </Band>
            <Band position="bottom" style={{ background: `${accent}33` }}>
              <div style={{ display: 'flex', gap: 2 }}>
                <i style={{ width: 14, height: 2, borderRadius: 1, background: accent, opacity: 0.55 }} />
                <i style={{ width: 6, height: 2, borderRadius: 1, background: accent, opacity: 0.4 }} />
              </div>
            </Band>
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, padding: '0 12px', display: 'flex', alignItems: 'center', gap: 10 }}>
              <i style={{ width: 8, height: 8, borderRadius: 2, background: accent, opacity: 0.55, flex: '0 0 auto' }} />
              <div style={{ fontSize: 12.5, color: accent, fontWeight: 500 }}>Pick a task type</div>
              <div style={{ marginLeft: 'auto', fontSize: 10.5, color: accent, opacity: 0.7, fontFamily: 'JetBrains Mono, monospace' }}>8:00–8:45</div>
            </div>
          </div>
        </div>

        {/* RESTING */}
        <div style={blockBox(STATE_ROWS[1].start, STATE_ROWS[1].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%', background: tint, border: `1px solid ${tintDeeper}`, borderRadius: 4 }}>
            <div style={{ position: 'absolute', top: 6, bottom: 6, left: 0, width: 3, borderRadius: 2, background: accent }} />
            <CrispBand position="top" tone={accent} alpha={0.4} />
            <CrispBand position="bottom" tone={accent} alpha={0.4} />
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, padding: '0 12px 0 14px', display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 3 }}>
              <div style={{ fontSize: 13, color: ink, fontWeight: 600 }}>Deep work · draft</div>
              <div style={{ fontSize: 11, color: mute, fontFamily: 'JetBrains Mono, monospace' }}>9:00–9:45 AM</div>
            </div>
          </div>
        </div>

        {/* SELECTED */}
        <div style={blockBox(STATE_ROWS[2].start, STATE_ROWS[2].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%', background: '#fff', border: `1.5px solid ${accent}`, borderRadius: 4, boxShadow: `0 0 0 4px ${accent}1a, 0 6px 18px rgba(31,79,150,0.08)` }}>
            <div style={{ position: 'absolute', top: 0, bottom: 0, left: 0, width: 5, background: accent }} />
            <CrispBand position="top" tone={accent} />
            <CrispBand position="bottom" tone={accent} />
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, padding: '0 12px 0 16px', display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 4 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ fontSize: 13.5, color: ink, fontWeight: 700 }}>Standup</div>
                <div style={{ fontSize: 10, color: accentDeep, fontFamily: 'JetBrains Mono, monospace', textTransform: 'uppercase', letterSpacing: '0.1em', background: `${accent}1f`, padding: '2px 6px', borderRadius: 3 }}>editing</div>
              </div>
              <div style={{ fontSize: 11, color: mute, fontFamily: 'JetBrains Mono, monospace' }}>10:00–11:00 AM · 1h</div>
              <div style={{ display: 'flex', gap: 6, marginTop: 2 }}>
                <button style={{ fontSize: 10.5, color: '#fff', background: accent, border: 'none', padding: '4px 8px', borderRadius: 3, fontWeight: 500 }}>Complete</button>
                <button style={{ fontSize: 10.5, color: accent, background: 'transparent', border: `1px solid ${accent}55`, padding: '4px 8px', borderRadius: 3, fontWeight: 500 }}>Delete</button>
              </div>
            </div>
          </div>
        </div>

        {/* DRAG */}
        <div style={blockBox(STATE_ROWS[3].start, STATE_ROWS[3].end)}>
          <div style={{ position: 'absolute', inset: 0, border: `1px dashed ${accent}88`, borderRadius: 4, background: `${accent}10` }} />
          <div style={{ position: 'absolute', inset: 0, transform: 'translate(8px,-10px) scale(1.02)', background: '#fff', border: `1.5px solid ${accent}`, borderRadius: 4, padding: '10px 12px 10px 16px', boxShadow: `0 22px 40px ${accent}38, 0 2px 6px rgba(31,79,150,0.18)` }}>
            <div style={{ position: 'absolute', top: 0, bottom: 0, left: 0, width: 5, background: accent }} />
            <div style={{ fontSize: 13, color: ink, fontWeight: 600 }}>Letter to S.</div>
            <div style={{ fontSize: 11, color: mute, fontFamily: 'JetBrains Mono, monospace', marginTop: 2 }}>11:15–12:00 PM</div>
          </div>
        </div>
      </Lane>
    </ArtboardShell>
  );
}

/* ──────────────────────────────────────────────────────────
   VARIANT 4 · Engraved — pressed/extruded depth
   Resize bands: indented grooves with parallel ink lines.
   ────────────────────────────────────────────────────────── */
function VariantEngraved() {
  const ink = '#1f2426';
  const mute = '#7a7368';
  const paper = '#f4f1e9';
  const paperDeep = '#e8e3d4';

  function GrooveBand({ position, strong = false }) {
    return (
      <Band
        position={position}
        style={{
          background: strong ? 'rgba(50,40,20,0.10)' : 'rgba(50,40,20,0.05)',
          boxShadow: 'inset 0 1px 2px rgba(50,40,20,0.12), inset 0 -1px 0 rgba(255,255,255,0.65)',
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          <div style={{ width: 36, height: 1, background: strong ? ink : 'rgba(50,40,20,0.45)' }} />
          <div style={{ width: 36, height: 1, background: strong ? ink : 'rgba(50,40,20,0.45)' }} />
        </div>
      </Band>
    );
  }

  return (
    <ArtboardShell
      kicker="04 / pressed · raised · paper depth"
      title="Engraved"
      footer="Bands · grooved recesses · two parallel ink rules"
    >
      <Lane tint={paper} gridTone="#dcd6c4">
        {/* DRAFT */}
        <div style={blockBox(STATE_ROWS[0].start, STATE_ROWS[0].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%', background: paperDeep, borderRadius: 6, boxShadow: 'inset 0 2px 5px rgba(50,40,20,0.12), inset 0 -1px 0 rgba(255,255,255,0.6)', border: '1px dashed rgba(80,70,50,0.25)' }}>
            <GrooveBand position="top" />
            <GrooveBand position="bottom" />
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, padding: '0 14px', display: 'flex', alignItems: 'center', gap: 10 }}>
              <span style={{ fontSize: 12.5, color: 'rgba(80,70,50,0.5)', fontStyle: 'italic' }}>waiting for a name</span>
              <span style={{ marginLeft: 'auto', fontSize: 10.5, color: 'rgba(80,70,50,0.45)', fontFamily: 'JetBrains Mono, monospace' }}>08:00–08:45</span>
            </div>
          </div>
        </div>

        {/* RESTING */}
        <div style={blockBox(STATE_ROWS[1].start, STATE_ROWS[1].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%', background: '#ede8d8', borderRadius: 6, boxShadow: 'inset 0 1px 2px rgba(50,40,20,0.08), inset 0 -1px 0 rgba(255,255,255,0.7)' }}>
            <GrooveBand position="top" />
            <GrooveBand position="bottom" />
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, padding: '0 14px', display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 3 }}>
              <div style={{ fontSize: 12.5, color: ink, fontWeight: 500 }}>Deep work · draft</div>
              <div style={{ fontSize: 10.5, color: mute, fontFamily: 'JetBrains Mono, monospace' }}>09:00 → 09:45</div>
            </div>
          </div>
        </div>

        {/* SELECTED */}
        <div style={blockBox(STATE_ROWS[2].start, STATE_ROWS[2].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%', background: '#fefcf5', borderRadius: 6, boxShadow: '0 1px 0 rgba(255,255,255,0.9) inset, 0 -1px 0 rgba(80,70,50,0.08) inset, 0 10px 24px rgba(50,40,20,0.10), 0 2px 4px rgba(50,40,20,0.06)' }}>
            <div style={{ position: 'absolute', top: 8, bottom: 8, left: 0, width: 2, background: ink, borderRadius: 2 }} />
            <GrooveBand position="top" strong />
            <GrooveBand position="bottom" strong />
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, padding: '0 14px', display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 4 }}>
              <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between' }}>
                <div style={{ fontSize: 13.5, color: ink, fontWeight: 600 }}>Standup</div>
                <div style={{ fontSize: 9.5, color: mute, fontFamily: 'JetBrains Mono, monospace', letterSpacing: '0.1em', textTransform: 'uppercase' }}>· selected</div>
              </div>
              <div style={{ fontSize: 10.5, color: mute, fontFamily: 'JetBrains Mono, monospace' }}>10:00 → 11:00 · 60m</div>
            </div>
          </div>
        </div>

        {/* DRAG */}
        <div style={blockBox(STATE_ROWS[3].start, STATE_ROWS[3].end)}>
          <div style={{ position: 'absolute', inset: 0, background: paperDeep, borderRadius: 6, boxShadow: 'inset 0 2px 5px rgba(50,40,20,0.18)' }} />
          <div style={{ position: 'absolute', inset: 0, transform: 'translate(10px,-12px) rotate(-1.2deg)', background: '#fefcf5', borderRadius: 6, padding: '10px 14px', boxShadow: '0 1px 0 rgba(255,255,255,0.9) inset, 0 28px 48px rgba(50,40,20,0.18), 0 4px 10px rgba(50,40,20,0.10)' }}>
            <div style={{ position: 'absolute', top: 8, bottom: 8, left: 0, width: 2, background: ink, borderRadius: 2 }} />
            <div style={{ fontSize: 12.5, color: ink, fontWeight: 500 }}>Letter to S.</div>
            <div style={{ fontSize: 10.5, color: mute, fontFamily: 'JetBrains Mono, monospace', marginTop: 3 }}>11:15 → 12:00</div>
          </div>
        </div>
      </Lane>
    </ArtboardShell>
  );
}

/* ──────────────────────────────────────────────────────────
   VARIANT 5 · Ticket — perforated, postal
   Resize bands: the perforation IS the band. Pull it.
   ────────────────────────────────────────────────────────── */
function VariantTicket() {
  const ink = '#1a1714';
  const mute = '#867f70';
  const cream = '#faf6ec';
  const accent = '#8a3a26';

  function Notches({ color = cream }) {
    return (
      <>
        <i style={{ position: 'absolute', top: -5, left: -5, width: 10, height: 10, borderRadius: '50%', background: color }} />
        <i style={{ position: 'absolute', top: -5, right: -5, width: 10, height: 10, borderRadius: '50%', background: color }} />
        <i style={{ position: 'absolute', bottom: -5, left: -5, width: 10, height: 10, borderRadius: '50%', background: color }} />
        <i style={{ position: 'absolute', bottom: -5, right: -5, width: 10, height: 10, borderRadius: '50%', background: color }} />
      </>
    );
  }

  function PerfBand({ position, tone = mute, dashed = true, dark = false }) {
    return (
      <Band position={position} style={{ background: dark ? ink : 'transparent', borderBottom: position === 'top' ? `1px ${dashed ? 'dashed' : 'solid'} ${tone}` : 'none', borderTop: position === 'bottom' ? `1px ${dashed ? 'dashed' : 'solid'} ${tone}` : 'none' }}>
        <div style={{ display: 'flex', gap: 5, alignItems: 'center' }}>
          {Array.from({ length: 10 }).map((_, i) => (
            <i key={i} style={{ width: 2, height: 2, borderRadius: '50%', background: dark ? cream : tone, display: 'block' }} />
          ))}
        </div>
      </Band>
    );
  }

  return (
    <ArtboardShell
      kicker="05 / ticket · perforated · postal"
      title="Ticket"
      footer="Bands · perforations top + bottom · pull to tear"
      accent={accent}
    >
      <Lane tint={cream} gridTone="#e6dfca">
        {/* DRAFT */}
        <div style={blockBox(STATE_ROWS[0].start, STATE_ROWS[0].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%', border: `1px dashed ${mute}`, background: 'transparent' }}>
            <Notches />
            <PerfBand position="top" />
            <PerfBand position="bottom" />
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, padding: '0 10px', display: 'flex', alignItems: 'center', gap: 14 }}>
              <span style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 10, color: mute, letterSpacing: '0.1em' }}>08:00 — 08:45</span>
              <span style={{ fontSize: 11, color: mute, fontStyle: 'italic' }}>tear and assign…</span>
              <span style={{ marginLeft: 'auto', fontFamily: 'JetBrains Mono, monospace', fontSize: 9.5, color: mute, letterSpacing: '0.12em' }}>№.draft</span>
            </div>
          </div>
        </div>

        {/* RESTING */}
        <div style={blockBox(STATE_ROWS[1].start, STATE_ROWS[1].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%', background: '#fffcf3', border: `1px solid ${mute}55`, boxShadow: '0 1px 0 rgba(80,70,50,0.06)' }}>
            <Notches />
            <PerfBand position="top" />
            <PerfBand position="bottom" />
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, padding: '0 10px', display: 'flex', alignItems: 'center', gap: 14 }}>
              <span style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 10, color: ink, letterSpacing: '0.08em' }}>09:00 — 09:45</span>
              <div style={{ fontSize: 13, color: ink, fontWeight: 500 }}>Deep work · draft</div>
              <span style={{ marginLeft: 'auto', fontFamily: 'JetBrains Mono, monospace', fontSize: 9.5, color: mute, letterSpacing: '0.12em' }}>№.0042</span>
            </div>
          </div>
        </div>

        {/* SELECTED */}
        <div style={blockBox(STATE_ROWS[2].start, STATE_ROWS[2].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%', background: '#fffcf3', border: `1px solid ${ink}`, boxShadow: `0 0 0 3px ${cream}, 0 0 0 4px ${ink}, 0 8px 22px rgba(50,40,20,0.12)` }}>
            <Notches color={cream} />
            <PerfBand position="top" dark />
            <PerfBand position="bottom" dark />
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, padding: '0 12px', display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 3 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 10, color: ink, letterSpacing: '0.08em' }}>10:00 — 11:00</div>
                <div style={{ width: 22, height: 14, border: `1px dashed ${accent}`, background: `${accent}10`, fontFamily: 'JetBrains Mono, monospace', fontSize: 8, color: accent, display: 'flex', alignItems: 'center', justifyContent: 'center', letterSpacing: '0.04em' }}>1h</div>
              </div>
              <div style={{ fontSize: 14, color: ink, fontWeight: 600 }}>Standup</div>
              <div style={{ fontSize: 10.5, color: mute }}>team · ★ selected</div>
            </div>
          </div>
        </div>

        {/* DRAG */}
        <div style={blockBox(STATE_ROWS[3].start, STATE_ROWS[3].end)}>
          <div style={{ position: 'absolute', inset: 0, border: `1px dashed ${mute}`, background: 'transparent' }}>
            <Notches />
          </div>
          <div style={{ position: 'absolute', inset: 0, transform: 'translate(12px,-12px) rotate(-2deg)', background: '#fffcf3', border: `1px solid ${ink}`, boxShadow: '0 22px 36px rgba(50,40,20,0.16), 0 3px 8px rgba(50,40,20,0.08)' }}>
            <Notches />
            <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: 18, borderBottom: `1px dashed ${mute}55`, display: 'flex', alignItems: 'center', padding: '0 10px' }}>
              <span style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 10, color: ink, letterSpacing: '0.08em' }}>11:15 — 12:00</span>
              <span style={{ marginLeft: 'auto', fontFamily: 'JetBrains Mono, monospace', fontSize: 9.5, color: mute, letterSpacing: '0.12em' }}>↕ moving</span>
            </div>
            <div style={{ position: 'absolute', top: 20, bottom: 4, left: 10, right: 10 }}>
              <div style={{ fontSize: 12.5, color: ink, fontWeight: 500 }}>Letter to S.</div>
            </div>
          </div>
        </div>
      </Lane>
    </ArtboardShell>
  );
}

/* ──────────────────────────────────────────────────────────
   VARIANT 6 · Stamp — bold mono numerals as identity
   Resize bands: solid tonal bars; ochre on select.
   ────────────────────────────────────────────────────────── */
function VariantStamp() {
  const ink = '#0c0a09';
  const mute = '#a8a29e';
  const tint = '#eeece6';
  const accent = '#d2a23f';

  function StampBand({ position, tone, dashed = false }) {
    return (
      <Band
        position={position}
        style={{
          background: dashed ? 'transparent' : tone,
          borderTop: dashed && position === 'bottom' ? `1.5px dashed ${tone}` : undefined,
          borderBottom: dashed && position === 'top' ? `1.5px dashed ${tone}` : undefined,
        }}
      >
        <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
          {Array.from({ length: 3 }).map((_, i) => (
            <i key={i} style={{ width: 10, height: 2, background: dashed ? tone : 'rgba(255,255,255,0.7)', display: 'block' }} />
          ))}
        </div>
      </Band>
    );
  }

  return (
    <ArtboardShell
      kicker="06 / mono · bold · monolithic"
      title="Stamp"
      footer="Bands · solid tonal bars · ochre on select"
      accent={accent}
    >
      <Lane tint="#f6f4ed" gridTone="#e5e0cf">
        {/* DRAFT */}
        <div style={blockBox(STATE_ROWS[0].start, STATE_ROWS[0].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%', border: `1.5px dashed #c9c3b1`, background: 'transparent' }}>
            <StampBand position="top" tone="#c9c3b1" dashed />
            <StampBand position="bottom" tone="#c9c3b1" dashed />
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, padding: '0 14px', display: 'flex', alignItems: 'center', gap: 16 }}>
              <div style={{ fontFamily: 'JetBrains Mono, monospace', fontWeight: 700, fontSize: 22, color: '#c9c3b1', letterSpacing: '-0.02em', lineHeight: 1 }}>—:—</div>
              <div style={{ fontSize: 11.5, color: '#c9c3b1', textTransform: 'uppercase', letterSpacing: '0.14em', fontFamily: 'Inter Tight, sans-serif' }}>unfilled</div>
            </div>
          </div>
        </div>

        {/* RESTING */}
        <div style={blockBox(STATE_ROWS[1].start, STATE_ROWS[1].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%', background: tint }}>
            <StampBand position="top" tone="rgba(20,15,10,0.18)" />
            <StampBand position="bottom" tone="rgba(20,15,10,0.18)" />
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, padding: '0 14px', display: 'flex', alignItems: 'center', gap: 16 }}>
              <div style={{ fontFamily: 'JetBrains Mono, monospace', fontWeight: 700, fontSize: 22, color: ink, letterSpacing: '-0.02em', lineHeight: 1 }}>09:00</div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 12, color: ink, textTransform: 'uppercase', letterSpacing: '0.14em', fontWeight: 600, fontFamily: 'Inter Tight, sans-serif' }}>Deep work</div>
                <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 10, color: mute, marginTop: 3 }}>45m</div>
              </div>
            </div>
          </div>
        </div>

        {/* SELECTED — inverted */}
        <div style={blockBox(STATE_ROWS[2].start, STATE_ROWS[2].end)}>
          <div style={{ position: 'relative', width: '100%', height: '100%', background: ink, boxShadow: `0 0 0 3px #f6f4ed, 0 0 0 4px ${accent}` }}>
            <StampBand position="top" tone={accent} />
            <StampBand position="bottom" tone={accent} />
            <div style={{ position: 'absolute', inset: `${BAND_H}px 0`, padding: '0 14px', display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 4 }}>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 16 }}>
                <div style={{ fontFamily: 'JetBrains Mono, monospace', fontWeight: 700, fontSize: 28, color: '#fdfcf6', letterSpacing: '-0.03em', lineHeight: 1 }}>10:00</div>
                <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 11, color: accent, letterSpacing: '0.04em' }}>→ 11:00</div>
              </div>
              <div style={{ fontSize: 11.5, color: '#fdfcf6', textTransform: 'uppercase', letterSpacing: '0.16em', fontWeight: 600, fontFamily: 'Inter Tight, sans-serif' }}>Standup · team</div>
            </div>
            <div style={{ position: 'absolute', top: 12, right: 10, fontFamily: 'JetBrains Mono, monospace', fontSize: 9, color: accent, letterSpacing: '0.1em', textTransform: 'uppercase' }}>· sel</div>
          </div>
        </div>

        {/* DRAG */}
        <div style={blockBox(STATE_ROWS[3].start, STATE_ROWS[3].end)}>
          <div style={{ position: 'absolute', inset: 0, background: 'transparent', border: `1.5px dashed #c9c3b1` }} />
          <div style={{ position: 'absolute', inset: 0, transform: 'translate(10px,-12px) scale(1.04)', background: '#fdfcf6', padding: '6px 14px', boxShadow: `0 0 0 2px ${accent}, 0 22px 36px rgba(0,0,0,0.18), 0 4px 10px rgba(0,0,0,0.08)`, display: 'flex', alignItems: 'center', gap: 16 }}>
            <div style={{ fontFamily: 'JetBrains Mono, monospace', fontWeight: 700, fontSize: 22, color: ink, letterSpacing: '-0.02em', lineHeight: 1 }}>11:15</div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 12, color: ink, textTransform: 'uppercase', letterSpacing: '0.14em', fontWeight: 600, fontFamily: 'Inter Tight, sans-serif' }}>Letter to S.</div>
              <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 10, color: accent, marginTop: 3, letterSpacing: '0.06em' }}>↕ moving</div>
            </div>
          </div>
        </div>
      </Lane>
    </ArtboardShell>
  );
}

Object.assign(window, {
  VariantMonastic,
  VariantEditorial,
  VariantCrisp,
  VariantEngraved,
  VariantTicket,
  VariantStamp,
});
