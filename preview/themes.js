(() => {
  'use strict';
  const names=['Основная · синяя','Зелёная · Spotify','Светлая · PS5','Тёплая · графит'];
  let current=0;
  try{current=Number(localStorage.getItem('quiet-tv-preview-theme'))||0;}catch(_){}
  function apply(index){
    current=Math.max(0,Math.min(3,index));
    document.documentElement.dataset.theme=String(current);
    document.querySelector('.screen').dataset.theme=String(current);
    document.querySelector('#theme-select').value=String(current);
    try{localStorage.setItem('quiet-tv-preview-theme',String(current));}catch(_){}
  }
  function paint(color){document.querySelector('.screen').style.setProperty('--app-color',color);}
  function settings(box,close){
    const descriptions=['Синие акценты, прямоугольные кнопки','Зелёные акценты, мягкие контуры','Светлый фон, тёмные значки и плавные формы','Тёплый графит, янтарный акцент, строгие углы'];
    names.forEach((name,index)=>{
      const button=document.createElement('button');button.className='theme-choice';
      button.setAttribute('aria-pressed',String(index===current));
      const title=document.createElement('strong'),description=document.createElement('small');
      title.textContent=name;description.textContent=descriptions[index];button.append(title,description);
      button.onclick=()=>{apply(index);box.querySelectorAll('.theme-choice').forEach((b,i)=>b.setAttribute('aria-pressed',String(current===i)));};
      box.append(button);
    });
    const done=document.createElement('button');done.textContent='Готово';done.onclick=close;box.append(done);
    box.querySelector('.theme-choice').focus();
  }
  document.querySelector('#theme-select').addEventListener('change',event=>apply(Number(event.target.value)));
  apply(current);window.QuietThemes={paint,settings};
})();
