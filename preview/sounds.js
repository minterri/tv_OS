(() => {
  'use strict';
  let state = {enabled:true, volume:20, tone:0}, context, unlocked=false, last=0;
  try { Object.assign(state, JSON.parse(localStorage.getItem('quiet-tv-preview-sound')) || {}); } catch (_) {}
  const frequencies=[650,880,440];
  function save(){ try {localStorage.setItem('quiet-tv-preview-sound',JSON.stringify(state));} catch (_) {} }
  function unlock(){
    unlocked=true;
    const Audio=window.AudioContext||window.webkitAudioContext;
    if(!Audio)return;
    try{if(!context)context=new Audio();if(context.state==='suspended')context.resume().catch(()=>{});}catch(_){}
  }
  document.addEventListener('pointerdown',unlock,{passive:true});
  document.addEventListener('keydown',unlock);
  function play(){
    if(!unlocked||!context||context.state!=='running'||!state.enabled||state.volume<=0||performance.now()-last<90)return;
    last=performance.now();
    const start=context.currentTime, duration=.075, oscillator=context.createOscillator(), gain=context.createGain();
    oscillator.frequency.value=frequencies[Math.max(0,Math.min(2,Number(state.tone)||0))];
    const envelope=new Float32Array(80);
    for(let i=0;i<envelope.length;i++){const t=duration*i/(envelope.length-1);envelope[i]=.18*Math.max(0,Math.min(100,Number(state.volume)||0))/100*Math.sin(Math.PI*t/duration)**2*Math.exp(-t*20);}
    gain.gain.setValueCurveAtTime(envelope,start,duration);oscillator.connect(gain);gain.connect(context.destination);
    oscillator.start(start);oscillator.stop(start+duration);
    oscillator.onended=()=>{oscillator.disconnect();gain.disconnect();};
  }
  function settings(box,close){
    const enabled=document.createElement('input');enabled.type='checkbox';enabled.checked=!!state.enabled;
    const label=document.createElement('label');label.className='sound-check';label.append(enabled,document.createTextNode(' Звуки навигации'));
    enabled.onchange=()=>{state.enabled=enabled.checked;save();};
    const level=document.createElement('label'),volume=document.createElement('input');
    volume.type='range';volume.min=0;volume.max=100;volume.value=state.volume;volume.id='effect-volume';
    level.htmlFor=volume.id;level.textContent='Громкость эффектов: '+volume.value+'%';
    volume.oninput=()=>{state.volume=Number(volume.value);level.textContent='Громкость эффектов: '+state.volume+'%';save();};
    volume.onchange=play;
    const row=document.createElement('div');row.className='sound-tones';
    const tones=['Мягкий','Капля','Тёплый'];
    tones.forEach((tone,i)=>{const b=document.createElement('button');b.textContent=tone;b.setAttribute('aria-pressed',String(state.tone===i));b.onclick=()=>{state.tone=i;save();[...row.children].forEach((item,j)=>item.setAttribute('aria-pressed',String(i===j)));play();};row.append(b);});
    const hint=document.createElement('p');hint.textContent='Только эффекты Quiet TV. Громкость приложений не меняется.';
    const test=document.createElement('button');test.textContent='Прослушать';test.onclick=()=>{unlock();play();};
    const done=document.createElement('button');done.textContent='Готово';done.onclick=close;
    box.append(label,level,volume,row,hint,test,done);enabled.focus();
  }
  window.QuietAudio={play,settings};
})();
