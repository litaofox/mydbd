/* ============================================================
   mydbd 文档库前端脚本 v1.0.0 2026-10-05
   1) 门户 index.html：关键字搜索 + 状态筛选 + 计数
   2) 内容页：目录滚动高亮（scrollspy）
   纯原生 JS，无外部依赖
   ============================================================ */
(function () {
  'use strict';

  /* ---------- 门户：搜索与筛选 ---------- */
  var cards = Array.prototype.slice.call(document.querySelectorAll('.doc[data-k]'));
  if (cards.length > 0) {
    var input = document.getElementById('q');
    var countEl = document.getElementById('count');
    var filterBtns = Array.prototype.slice.call(document.querySelectorAll('.filters button[data-filter]'));
    var sections = Array.prototype.slice.call(document.querySelectorAll('section[data-section]'));
    var currentFilter = 'all';

    function norm(s) { return (s || '').toLowerCase(); }

    function apply() {
      var kw = norm(input ? input.value.trim() : '');
      var shown = 0;
      cards.forEach(function (card) {
        var hay = norm(card.getAttribute('data-k') + ' ' + card.textContent);
        var okKw = !kw || hay.indexOf(kw) !== -1;
        var status = card.getAttribute('data-status') || 'ref';
        var okStatus = currentFilter === 'all' || status === currentFilter;
        var visible = okKw && okStatus;
        card.style.display = visible ? '' : 'none';
        if (visible) shown++;
      });
      // 隐藏空分区
      sections.forEach(function (sec) {
        var any = sec.querySelectorAll('.doc[data-k]').length > 0 &&
          Array.prototype.some.call(sec.querySelectorAll('.doc[data-k]'),
            function (c) { return c.style.display !== 'none'; });
        sec.style.display = any ? '' : 'none';
      });
      if (countEl) countEl.textContent = kw || currentFilter !== 'all'
        ? '命中 ' + shown + ' / ' + cards.length + ' 份'
        : '共 ' + cards.length + ' 份文档';
      var empty = document.getElementById('empty');
      if (empty) empty.style.display = shown === 0 ? 'block' : 'none';
    }

    if (input) input.addEventListener('input', apply);
    filterBtns.forEach(function (btn) {
      btn.addEventListener('click', function () {
        currentFilter = btn.getAttribute('data-filter');
        filterBtns.forEach(function (b) { b.classList.toggle('on', b === btn); });
        apply();
      });
    });
    apply();
  }

  /* ---------- 内容页：目录高亮 ---------- */
  var tocLinks = Array.prototype.slice.call(document.querySelectorAll('.toc-inner a[href^="#"]'));
  if (tocLinks.length > 0 && 'IntersectionObserver' in window) {
    var map = {};
    tocLinks.forEach(function (a) {
      var id = decodeURIComponent(a.getAttribute('href').slice(1));
      var target = document.getElementById(id);
      if (target) map[id] = a;
    });
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        var a = map[entry.target.id];
        if (!a) return;
        if (entry.isIntersecting) {
          tocLinks.forEach(function (x) { x.classList.remove('active'); });
          a.classList.add('active');
        }
      });
    }, { rootMargin: '-20% 0px -70% 0px' });
    Object.keys(map).forEach(function (id) { observer.observe(document.getElementById(id)); });
  }
})();
