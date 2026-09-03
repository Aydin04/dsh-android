(function() {
  if (window.__dshAgentNotifierPatched) return;
  window.__dshAgentNotifierPatched = true;

  document.addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && !e.shiftKey && !e.ctrlKey && !e.altKey && !e.metaKey) {
      var target = e.target;
      if (target && (target.tagName === 'TEXTAREA' || target.isContentEditable || target.getAttribute('role') === 'textbox')) {
        e.stopPropagation();
      }
    }
  }, true);

  var lastNotifiedText = '';
  var settleTimer = null;
  var isGenerating = false;

  function isVisible(el) {
    if (!el) return false;
    if (el.offsetWidth === 0 && el.offsetHeight === 0) return false;
    var style = window.getComputedStyle ? window.getComputedStyle(el) : null;
    if (style && (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0')) return false;
    return true;
  }

  function checkIsGenerating() {
    var stopBtns = document.querySelectorAll('button[aria-label*="stop" i], button[title*="stop" i], [class*="stop" i], button[aria-label*="hentikan" i]');
    for (var i = 0; i < stopBtns.length; i++) {
      if (isVisible(stopBtns[i])) return true;
    }
    var busyEls = document.querySelectorAll('[aria-busy="true"], [data-status="running"], [data-status="busy"], [data-status="generating"], [class*="loading" i], [class*="spinner" i]');
    for (var j = 0; j < busyEls.length; j++) {
      if (isVisible(busyEls[j])) return true;
    }
    return false;
  }

  function isPureTimerOrNumbers(str) {
    var cleaned = str.replace(/[0-9.: smsecminsecondsthougthinking()/-]/gi, '').trim();
    return cleaned.length === 0;
  }

  function getCleanAssistantAnswer() {
    var candidates = document.querySelectorAll('[data-role="assistant"], [class*="assistant" i], [data-author="assistant"], [data-role="agent"], [class*="agent" i], article');
    var validTurns = [];
    for (var i = 0; i < candidates.length; i++) {
      var el = candidates[i];
      if (el.closest('[data-role="user"], [class*="user" i], [class*="human" i], [data-author="user"]')) continue;
      if (el.matches('[data-role="user"], [class*="user" i], [class*="human" i], [data-author="user"]')) continue;
      if (el.closest('[class*="composer" i], form, [role="form"], [class*="header" i]')) continue;
      if (el.querySelector('textarea, input, [role="textbox"]')) continue;
      if (el.classList.contains('composer') || (el.className && typeof el.className === 'string' && el.className.toLowerCase().includes('composer'))) continue;
      validTurns.push(el);
    }
    var targetNode = null;
    if (validTurns.length > 0) {
      targetNode = validTurns[validTurns.length - 1];
    } else {
      var proseList = document.querySelectorAll('[class*="prose" i], [class*="markdown" i], .dsh-markdown');
      for (var k = proseList.length - 1; k >= 0; k--) {
        var pEl = proseList[k];
        if (pEl.closest('[data-role="user"], [class*="user" i], [class*="human" i], [class*="composer" i]')) continue;
        targetNode = pEl;
        break;
      }
    }
    if (!targetNode) return '';
    var clone = targetNode.cloneNode(true);
    var unwanted = clone.querySelectorAll('details, summary, [class*="role" i], [class*="badge" i], [class*="header" i], [class*="thought" i], [class*="thinking" i], [class*="timer" i], [class*="duration" i], [class*="status" i], [class*="step" i], [class*="trajectory" i], [class*="deep" i], [class*="avatar" i], [class*="icon" i], [class*="tool" i], [class*="action" i], [class*="call" i], button, svg, [class*="composer" i]');
    for (var j = 0; j < unwanted.length; j++) { unwanted[j].remove(); }
    var text = (clone.innerText || clone.textContent || '').trim();
    text = text.replace(/^(ASSISTANT|USER|AGENT|DEEPSEEK)[ :\n]*/i, '').trim();
    if (!text || text.length < 5) return '';
    if (isPureTimerOrNumbers(text)) return '';
    if (text.toLowerCase() === 'send message' || text.toLowerCase() === 'send' || text.toLowerCase() === 'kirim' || text.toLowerCase() === 'assistant') return '';
    return text;
  }

  function processUpdate() {
    var currentlyGenerating = checkIsGenerating();
    if (currentlyGenerating) {
      isGenerating = true;
      return;
    }
    var answer = getCleanAssistantAnswer();
    if (!answer || answer.length < 5 || answer === lastNotifiedText) return;

    clearTimeout(settleTimer);
    // Instant ultra-low latency debounce (200ms)
    settleTimer = setTimeout(function() {
      var finalAnswer = getCleanAssistantAnswer();
      if (finalAnswer && finalAnswer !== lastNotifiedText && finalAnswer.length >= 5 && !checkIsGenerating()) {
        lastNotifiedText = finalAnswer;
        isGenerating = false;
        if (window.AndroidBridge && window.AndroidBridge.notifyAgentReply) {
          window.AndroidBridge.notifyAgentReply(finalAnswer);
        }
      }
    }, 200);
  }

  var observer = new MutationObserver(processUpdate);
  observer.observe(document.body, { childList: true, subtree: true, characterData: true });
  setInterval(processUpdate, 250);
})();
