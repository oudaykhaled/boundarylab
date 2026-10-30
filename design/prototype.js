'use strict';
const files = [
  { name: 'inspection-note.txt', size: 181, sha256: 'd31091d8d9ec0dff457b8f679c1a604aa418fb72f43b50590eda3a5a8929608f' },
  { name: 'inspection-schematic.png', size: 130, sha256: 'dbad0f985de8012f2e79a6a730debfff95701fbdc2b7bf3d1ba58cb699cf1858' },
  { name: 'sensor-readings.csv', size: 69, sha256: 'a092da6fa3e3e9af96ad3984b81f904b80b9cbcaade5bea44cca68834bebea10' }
];
const $ = id => document.getElementById(id);
let phase = 'selected', selected = files.map(() => true), attempt = [], done = 0, cancelRequested = false;
const history = [];
files.forEach((file, index) => {
  const row = document.createElement('div'); row.className = 'attachment';
  row.innerHTML = `<input type="checkbox" checked id="file-${index}"><label for="file-${index}"><span class="alias">${file.name}</span><span class="file-status" id="file-state-${index}">${file.size} bytes · Selected</span></label>`;
  $('attachments').append(row);
  $(`file-${index}`).addEventListener('change', event => { selected[index] = event.target.checked; updateSelection(); });
});
function updateSelection() {
  const count = selected.filter(Boolean).length;
  $('summary').textContent = `${count} file${count === 1 ? '' : 's'} selected`;
  $('total').textContent = `${files.reduce((sum, file, i) => sum + (selected[i] ? file.size : 0), 0)} bytes`;
  files.forEach((file, i) => { $(`file-state-${i}`).textContent = `${file.size} bytes · ${selected[i] ? 'Selected' : 'Not selected'}`; });
  $('primary').disabled = !count;
  showState(count ? 'Ready to prepare' : 'Select attachments', count ? 'Public aliases above are the filenames your recipient needs.' : 'Choose at least one reference attachment to explore the flow.');
}
function showState(title, copy, tone = '', focus = false) {
  $('state-title').textContent = title; $('state-copy').textContent = copy;
  $('status-panel').className = `status-panel ${tone}`;
  if (focus) $('state-title').focus();
}
function setStep(step) {
  ['select', 'prepare', 'export'].forEach(value => { const node = $(`step-${value}`); node.removeAttribute('aria-current'); if (step === value) node.setAttribute('aria-current', 'step'); });
}
function record(outcome) {
  history.unshift({ number: history.length + 1, count: attempt.length, outcome });
  $('history-count').textContent = history.length;
}
function finish(outcome) {
  phase = outcome; $('cancel').hidden = true; $('primary').hidden = outcome !== 'complete'; $('new').hidden = false;
  const messages = {
    complete: ['Pack ready', 'Every selected reference file succeeded in this simulated attempt.', 'success'],
    cancelled: ['Preparation cancelled', 'No complete manifest is available. A new attempt processes all selected files again.', 'warning'],
    failed: ['Pack could not be prepared', 'An attachment could not be read. No complete manifest is available.', 'danger'],
    lost: ['Connection lost', 'The outcome is unknown. This attempt cannot be exported. Reconnect before starting again.', 'danger']
  };
  showState(...messages[outcome], true); $('progress').textContent = `Simulation: ${outcome}.`;
  record(outcome); if (outcome === 'complete') { setStep('export'); $('primary').textContent = 'Download sample manifest'; }
}
function prepare() {
  phase = 'processing'; attempt = files.map((_, i) => i).filter(i => selected[i]); done = 0; cancelRequested = false;
  const scenario = $('scenario').value;
  files.forEach((_, i) => { $(`file-${i}`).disabled = true; if (selected[i]) $(`file-state-${i}`).textContent = 'Waiting'; });
  $('primary').hidden = true; $('new').hidden = true; $('cancel').hidden = false; $('cancel').disabled = false;
  $('cancel').textContent = 'Request cancellation'; setStep('prepare'); $('attempt-title').textContent = 'Prepare attachments';
  showState('Preparing pack', 'Keep this session open. Progress below is simulated.', '', true);
  function tick() {
    const i = attempt[done]; $(`file-state-${i}`).textContent = 'Processing';
    $('progress').textContent = `File ${done + 1} of ${attempt.length}`;
    setTimeout(() => {
      if (cancelRequested) { $(`file-state-${i}`).textContent = 'Cancelled'; finish('cancelled'); return; }
      if (scenario !== 'success' && done === Math.min(1, attempt.length - 1)) {
        $(`file-state-${i}`).textContent = scenario === 'failure' ? 'Failed · Unsupported read' : 'Lost · Outcome unknown';
        finish(scenario === 'failure' ? 'failed' : 'lost'); return;
      }
      $(`file-state-${i}`).textContent = '✓ Complete · Simulated'; done += 1;
      if (done === attempt.length) finish('complete'); else tick();
    }, 1500);
  }
  tick();
}
function exportSample() {
  if (phase !== 'complete') return;
  const mode = $('export-scenario').value;
  if (mode === 'cancel') { showState('Pack ready', 'Export was cancelled. No export was confirmed.', '', true); return; }
  if (mode === 'fail') { showState('Export failed', 'The simulated destination could not be written. Your prepared sample remains available in this session.', 'danger', true); return; }
  const manifest = { format: 'fieldpack', version: 1, algorithm: 'sha256', files: attempt.map(i => files[i]) };
  const url = URL.createObjectURL(new Blob([JSON.stringify(manifest) + '\n'], { type: 'application/json' }));
  const link = document.createElement('a'); link.href = url; link.download = 'reference-sample.fieldpack.json'; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  showState('Sample download requested', 'Your browser received the reference manifest. Saving to disk is not confirmed. Transfer the matching reference fixtures separately.', 'success', true);
}
$('primary').addEventListener('click', () => phase === 'complete' ? exportSample() : prepare());
$('cancel').addEventListener('click', () => { cancelRequested = true; $('cancel').disabled = true; $('cancel').textContent = 'Cancellation requested'; showState('Cancellation requested', 'Waiting for the current read to finish. This can take time.', 'warning'); $('progress').textContent = 'Cancellation requested; cleanup is still pending.'; });
$('new').addEventListener('click', () => { phase = 'selected'; files.forEach((_, i) => { $(`file-${i}`).disabled = false; }); $('primary').hidden = false; $('primary').textContent = 'Prepare sample pack'; $('new').hidden = true; $('attempt-title').textContent = 'Select attachments'; $('progress').textContent = ''; setStep('select'); updateSelection(); $('state-title').focus(); });
$('history-open').addEventListener('click', () => { $('history-list').replaceChildren(); if (!history.length) { const li = document.createElement('li'); li.textContent = 'No attempts in this session.'; $('history-list').append(li); } history.forEach(item => { const li = document.createElement('li'); li.textContent = `Attempt ${item.number} · ${item.count} files · ${item.outcome} (simulated)`; $('history-list').append(li); }); $('history').showModal(); });
$('history-close').addEventListener('click', () => $('history').close());
$('history').addEventListener('close', () => $('history-open').focus());
$('rtl').addEventListener('click', () => { const rtl = $('device').dir !== 'rtl'; $('device').dir = rtl ? 'rtl' : 'ltr'; $('rtl').setAttribute('aria-pressed', String(rtl)); $('rtl').textContent = rtl ? 'Return to LTR layout' : 'Preview RTL layout'; });
