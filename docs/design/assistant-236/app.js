// THROWAWAY: fixture-only conversation review. No provider, API, or persistence.
const $=s=>document.querySelector(s), esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const names={A:'Inline schedule',B:'Compact disclosure',C:'Answer + schedule'};
let variant=new URLSearchParams(location.search).get('variant')||'A'; if(!names[variant])variant='A';
let turns=[], ended='', timer, generation=0, draft='', expanded=new Set(), away=false;
const blocks=[['09:00–10:30','Design review','Work / Product'],['11:00–12:00','Build the prototype','Work / Development'],['14:00–14:30','Read','Personal / Learning']];
const reply='There’s a 30-minute gap from 10:30 to 11:00, and two hours from noon to 14:00. These are gaps in your plan, not a record of time spent.';
function make(q='Show today’s plan',extra={}){return {q,text:'',status:'Complete',card:true,count:3,date:'21 Sep 2026',time:'09:41',...extra};}
function card(t,i){if(!t.card)return '';const rows=Array.from({length:t.count},(_,j)=>blocks[j%3]);const open=expanded.has(i);const shown=open?rows:rows.slice(0,3);const body=t.count?shown.map((b,j)=>`<div class="slot"><span>${t.count>3?`${String(7+j).padStart(2,'0')}:00–${String(8+j).padStart(2,'0')}:00`:b[0]}</span><div><strong>${esc(b[1])}</strong><small>${b[2]}</small></div></div>`).join(''):'<p>No Planned Blocks for this date.</p>';
const details=`<div class="brief-body">${body}${t.count>3?`<button data-expand="${i}" aria-expanded="${open}">${open?'Show fewer':`Show all ${t.count} blocks`}</button>`:''}</div>`;
const title=`<strong>${t.date==='21 Sep 2026'?'Today’s plan':'Plan snapshot'}</strong><small>${t.date} · ${t.count} Planned Blocks</small><small>Read at ${t.time} · Asia/Singapore</small>`;
return variant==='B'?`<details class="brief" data-card="${i}"><summary class="brief-title">${title}<span class="muted">View schedule</span></summary>${details}</details>`:`<section class="brief" aria-label="Plan snapshot"><div class="brief-title">${title}</div>${details}</section>`;}
function turn(t,i){let text=t.formatted?'<p>Your morning has <strong>two Planned Blocks</strong>.</p><ul><li><strong>09:00–10:30:</strong> Design review</li><li><strong>11:00–12:00:</strong> Build the prototype</li></ul><p>There is a <strong>30-minute gap</strong> between them.</p>':t.text?`<p>${esc(t.text)}</p>`:'';let warning=['Stopped','Interrupted','Read failed'].includes(t.status)?`<div class="notice"><strong>${t.status}</strong><br>This attempt won’t be used in later answers.${i===turns.length-1&&!ended?`<br><button data-retry="${i}">Retry response ↻</button>`:''}</div>`:'';
return `<div class="question">${esc(t.q)}</div><article class="answer"><div class="stamp">ASSISTANT${t.retry?' · RETRY':''}</div>${variant==='C'?text+card(t,i):card(t,i)+text}${t.status==='Streaming'?`<div class="progress busy" role="status">${t.card?'Writing response…':t.phase||'Reading today’s plan…'}</div>`:''}${warning}</article>`;}
function state(){ $('#state').textContent=JSON.stringify({variant,ended:ended||false,streaming:busy(),fixtureOnly:true,attempts:turns.map(t=>({status:t.status,visibleCard:t.card,retainedInMemory:t.status==='Complete',date:t.card?t.date:null})),draft,tab:away?'Day':'Assistant'},null,2);}
function busy(){return turns.at(-1)?.status==='Streaming';}
function content(follow=false){const main=$('main'),old=main.scrollTop,near=main.scrollHeight-old-main.clientHeight<90;const opened=[...main.querySelectorAll('details[open]')].map(x=>x.dataset.card);
main.innerHTML=away?'<h2>Day</h2><p>Navigation preview. Your Assistant response continues while you’re here.</p><button id="return">Return to Assistant</button>':turns.length?turns.map(turn).join(''):`<div class="welcome"><div class="mark">✳</div><h2>A little clarity<br>for today</h2><p>Ask about your Planned Blocks. Assistant can read your plan, but can’t change it.</p>${['Show today’s plan','Do I have a 30-minute gap?','Help me think through my morning'].map(q=>`<button class="starter">${q}</button>`).join('')}</div>`;
opened.forEach(i=>{const el=main.querySelector(`[data-card="${i}"]`);if(el)el.open=true;});
if(ended)main.insertAdjacentHTML('beforeend',`<div class="notice"><strong>${ended==='limit'?'Conversation limit reached':'Conversation expired'}</strong><p>${ended==='limit'?'You’ve reached 20 completed exchanges.':'This conversation ended after 60 minutes of inactivity.'} Your conversation remains visible.</p><button class="reset">New conversation →</button></div>`);
main.scrollTop=follow||near?main.scrollHeight:old;
$('#latest').hidden=main.scrollHeight-main.scrollTop-main.clientHeight<90||away;
$('#send').textContent=busy()?'■':'↑';$('#send').setAttribute('aria-label',busy()?'Stop response':'Send message');$('#send').disabled=!!ended||(!busy()&&!draft.trim());$('#draft').disabled=!!ended||away;
$('#composer').hidden=!!ended||away;$('#new-ended').hidden=!ended||away;
state();}
function stop(status='Stopped'){clearInterval(timer);generation++;if(busy())turns.at(-1).status=status;content();}
function start(q,retry=false,mode='normal'){if(busy()||ended)return;const t=make(q,{card:false,status:'Streaming',retry,mode});turns.push(t);draft='';$('#draft').value='';content(true);const token=++generation;let tick=0;
timer=setInterval(()=>{if(token!==generation)return;tick++;if(tick===3){t.card=mode!=='failure'&&!q.toLowerCase().includes('gap');t.count=mode==='empty'?0:3;t.phase='Writing response…';}
if(mode==='failure'&&tick===4){t.text='I couldn’t read your current plan. Please retry.';stop('Read failed');return;}
if(mode==='disconnect'&&tick===5){stop('Interrupted');return;}
if(tick>=5&&!q.toLowerCase().startsWith('show'))t.text=(mode==='empty'?'There are no Planned Blocks for this date.':reply).slice(0,(tick-4)*12);
if(tick>=18){t.status='Complete';clearInterval(timer);}content();},350);}
function scene(value){clearInterval(timer);generation++;ended='';away=false;expanded.clear();
turns=value==='welcome'?[]:[make('Walk me through today’s plan',{text:reply})];
if(value==='formatted')turns=[make('Help me think through my morning',{formatted:true})];
if(value==='cardonly')turns=[make()];if(value==='empty')turns=[make('Show today’s plan',{count:0})];
if(value==='failure')turns=[make('Show today’s plan',{card:false,status:'Read failed',text:'I couldn’t read your current plan. Please retry.'})];
if(value==='interrupted')turns=[make('Walk me through today’s plan',{status:'Interrupted',text:'There’s a 30-minute gap from 10:30 to 11:00. You could…'})];
if(value==='unconfirmed')turns=[make('Show today’s plan',{status:'Interrupted'})];
if(value==='longplan')turns=[make('Show today’s plan',{count:12})];
if(value==='long')turns=Array.from({length:8},(_,i)=>make(i?'Do I have a 30-minute gap?':'Show today’s plan',{card:i===0,text:i?reply:''}));
if(value==='midnight')turns=[make('Show today’s plan',{date:'20 Sep 2026',time:'23:55'}),make('Show today’s plan again',{time:'00:05'})];
if(value==='expired'||value==='limit')ended=value;
if(value==='streaming'){turns=[];start('Walk me through today’s plan');}else content(true);}
function layout(){ $('#phone').className='phone layout-'+variant+($('#dark').checked?' dark':'');$('#phone').style.width=$('#small').checked?'320px':'390px';$('#phone').classList.toggle('large',$('#large').checked);$('#keyboard-panel').hidden=!$('#keyboard').checked;$('#variant-label').textContent=variant+' — '+names[variant];$('#notes').textContent={A:'Recommended: a complete inline schedule, followed by interpretation. Long plans expand within the conversation.',B:'A compact disclosure keeps long conversations shorter. Open the schedule to inspect the full snapshot.',C:'Interpretation leads, with the schedule as supporting evidence below. Compare reading order while the answer streams.'}[variant];content();}
function change(d){variant=['A','B','C'][(['A','B','C'].indexOf(variant)+d+3)%3];history.replaceState(null,'','?variant='+variant);layout();}
document.addEventListener('click',e=>{const b=e.target.closest('button');if(!b)return;if(b.classList.contains('reset')){scene('welcome');draft='';$('#draft').value='';content();$('#draft').focus();}if(b.classList.contains('starter')){draft=b.textContent;$('#draft').value=draft;content();$('#draft').focus();}if(b.dataset.retry!==undefined)start(turns[+b.dataset.retry].q,true);if(b.dataset.expand!==undefined){const i=+b.dataset.expand;expanded.has(i)?expanded.delete(i):expanded.add(i);content();}if(b.id==='return'){away=false;content(true);}});
$('#send').onclick=()=>busy()?stop():draft.trim()&&start(draft.trim(),false,$('#outcome').value);
$('#draft').oninput=e=>{draft=e.target.value;e.target.style.height='auto';e.target.style.height=Math.min(e.target.scrollHeight,110)+'px';$('#send').disabled=!busy()&&!draft.trim();state();};
$('#scene').onchange=e=>scene(e.target.value);['dark','small','large','keyboard'].forEach(id=>$('#'+id).onchange=layout);
$('#prev').onclick=()=>change(-1);$('#next').onclick=()=>change(1);document.addEventListener('keydown',e=>{if(e.target.closest('input,textarea,select,[contenteditable],summary'))return;if(e.key==='ArrowLeft')change(-1);if(e.key==='ArrowRight')change(1);});
$('#latest').onclick=()=>{$('main').scrollTop=$('main').scrollHeight;};$('main').onscroll=()=>{$('#latest').hidden=$('main').scrollHeight-$('main').scrollTop-$('main').clientHeight<90||away;};
$('#interrupt').onclick=()=>stop('Interrupted');$('#day').onclick=()=>{away=true;content();};$('#assistant').onclick=()=>{away=false;content(true);};
scene('answer');layout();
