/* App shell: introduction + DesignCanvas of all six variants. */

const { useState } = React;

function Intro() {
  return (
    <div className="intro">
      <div style={{
        fontFamily: 'JetBrains Mono, monospace',
        fontSize: 11,
        letterSpacing: '0.16em',
        textTransform: 'uppercase',
        color: 'rgba(60,50,40,0.55)',
        marginBottom: 8,
      }}>
        Timebox · block atom
      </div>
      <h1>Six directions for the block, drawn in all four states.</h1>
      <p>
        The same scene in every artboard — a fragment of the planned lane between 8 AM and 12 PM — so
        the atoms compare cleanly. Each variant is a complete language: how an empty slot speaks, how a
        resting block sits, how selection earns weight, how the block lifts under press-hold — and how
        the <strong style={{ color: '#1f2426', fontWeight: 600 }}>start/end resize bands</strong> live
        inside every created block, ready to be grabbed.
      </p>
      <div className="legend">
        <span><i style={{background:'#cbc6b9'}}/>01 · empty draft</span>
        <span><i style={{background:'#8a857a'}}/>02 · created · idle</span>
        <span><i style={{background:'#1f2426'}}/>03 · selected</span>
        <span><i style={{background:'#c96442'}}/>04 · press · hold</span>
      </div>
    </div>
  );
}

function App() {
  // Each artboard is wider than tall to make room for the rail, lane, and annotations side by side.
  const W = 760;
  const H = 600;

  return (
    <div>
      <Intro />
      <DesignCanvas style={{ height: 'calc(100vh - 220px)', minHeight: 760 }}>
        <DCSection
          id="six-directions"
          title="Six directions"
          subtitle="Same scene, four states each · pick what feels right"
        >
          <DCArtboard id="01-monastic"  label="01 · Monastic — paper · hairline"        width={W} height={H}><VariantMonastic /></DCArtboard>
          <DCArtboard id="02-editorial" label="02 · Editorial — serif · mono index"     width={W} height={H}><VariantEditorial /></DCArtboard>
          <DCArtboard id="03-crisp"     label="03 · Crisp — accent · ringed · sharp"    width={W} height={H}><VariantCrisp /></DCArtboard>
          <DCArtboard id="04-engraved"  label="04 · Engraved — pressed · raised"        width={W} height={H}><VariantEngraved /></DCArtboard>
          <DCArtboard id="05-ticket"    label="05 · Ticket — perforated · postal"       width={W} height={H}><VariantTicket /></DCArtboard>
          <DCArtboard id="06-stamp"     label="06 · Stamp — bold mono numerals"         width={W} height={H}><VariantStamp /></DCArtboard>
        </DCSection>
      </DesignCanvas>
    </div>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
